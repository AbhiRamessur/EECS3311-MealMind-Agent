package com.mealmind.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Registers the agent's tools, describes them to the language model, parses tool calls out of model
 * replies and runs them safely.
 *
 * <p>Stage 1 described reflective discovery of tool methods. This implementation instead uses
 * <strong>explicit registration</strong> of tools that each declare a {@link ToolSpec}: it is
 * type-safe, easy to test, and needs no reflection.
 *
 * <p>The tool-call protocol is plain JSON text, {@code {"tool": "<name>", "arguments": {...}}}, which
 * may arrive bare, inside a {@code ```} fence, or surrounded by prose. Arguments are validated against
 * the spec before the tool runs, and every problem becomes a {@link ToolResult} error phrased so the
 * model can correct its next call; {@link #execute} never throws for bad input or tool failures.
 */
public final class ToolManager {

    /** Longest error message passed back from a failing tool. */
    static final int MAX_ERROR_LENGTH = 300;
    /** Longest successful result, in characters of compact JSON. */
    static final int MAX_RESULT_LENGTH = 8000;
    /** Most entries allowed in a string-array argument. */
    static final int MAX_ARRAY_ENTRIES = 20;

    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*(.*?)```",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final Map<String, Tool> tools = new TreeMap<>();
    private final AtomicLong executions = new AtomicLong();

    /**
     * Adds a tool.
     *
     * @param tool the tool
     * @throws NullPointerException     if {@code tool} is null
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    public void register(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        String name = tool.spec().name();
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("A tool named '" + name + "' is already registered");
        }
        tools.put(name, tool);
    }

    /**
     * Looks up a tool by name.
     *
     * @param name the tool name
     * @return the tool, or empty if none is registered under that name
     */
    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Returns every tool's spec.
     *
     * @return the specs, sorted by name
     */
    public List<ToolSpec> specs() {
        return tools.values().stream().map(Tool::spec).toList();
    }

    /**
     * Returns the JSON Schema of every tool.
     *
     * @return an array of {@link ToolSpec#toJsonSchema()} objects, sorted by name
     */
    public ArrayNode schemas() {
        ArrayNode array = ToolJson.MAPPER.createArrayNode();
        specs().forEach(spec -> array.add(spec.toJsonSchema()));
        return array;
    }

    /**
     * Describes the tools as text for a prompt: one line per tool,
     * {@code "- recipe_search(query?: string, ...): <description>"}, followed by one indented line per
     * argument, {@code "    query (string, optional): <description>"}. String-array arguments are
     * shown as {@code "array of strings"}.
     *
     * @return the description, sorted by tool name
     */
    public String describeForPrompt() {
        List<String> lines = new ArrayList<>();
        for (ToolSpec spec : specs()) {
            String signature = spec.params().stream()
                    .map(p -> p.name() + (p.required() ? "" : "?") + ": " + typeLabel(p.type()))
                    .collect(Collectors.joining(", ", spec.name() + "(", ")"));
            lines.add("- " + signature + ": " + spec.description());
            for (ToolParam param : spec.params()) {
                lines.add("    " + param.name() + " (" + typeLabel(param.type()) + ", "
                        + (param.required() ? "required" : "optional") + "): " + param.description());
            }
        }
        return String.join("\n", lines);
    }

    /** The type name shown to the model: "array of strings" is clearer than the schema's "array". */
    private static String typeLabel(ParamType type) {
        return type == ParamType.STRING_ARRAY ? "array of strings" : type.jsonType();
    }

    /**
     * Returns how many calls {@link #execute} has handled, successful or not.
     *
     * @return the count
     */
    public long executionCount() {
        return executions.get();
    }

    // ---- parsing model output ----

    /**
     * Extracts a tool call from a model reply. Accepts a bare JSON object, one inside a {@code ```} or
     * {@code ```json} fence, or the first complete {@code {...}} object embedded in prose. The object
     * needs a string field {@code "tool"} and may have an object field {@code "arguments"}.
     *
     * @param llmText the model's reply
     * @return the tool call
     * @throws ToolParseException if no valid tool call is found; the message says what to fix
     */
    public ToolCall parse(String llmText) {
        if (llmText == null || llmText.isBlank()) {
            throw new ToolParseException("The reply is empty; expected a JSON object like {\"tool\": \"...\", \"arguments\": {}}");
        }
        String objectText = objectText(llmText)
                .orElseThrow(() -> new ToolParseException("No JSON object found in the reply"));
        JsonNode root = extractJsonObject(llmText)
                .orElseThrow(() -> new ToolParseException("The reply contains invalid JSON: " + jsonError(objectText)));
        JsonNode tool = root.get("tool");
        if (tool == null || !tool.isTextual() || tool.asText().isBlank()) {
            throw new ToolParseException("The JSON must contain a string field named 'tool'");
        }
        JsonNode arguments = root.get("arguments");
        if (arguments != null && !arguments.isNull() && !arguments.isObject()) {
            throw new ToolParseException("'arguments' must be a JSON object");
        }
        return new ToolCall(tool.asText(), arguments);
    }

    /**
     * Finds the first JSON object in model output: bare, inside a {@code ```} or {@code ```json} fence,
     * or embedded in prose (the first balanced {@code {...}}, ignoring braces inside JSON strings).
     *
     * @param text the model's reply, may be null
     * @return the parsed object, or empty if there is none or it is not valid JSON
     */
    public Optional<JsonNode> extractJsonObject(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return objectText(text).flatMap(candidate -> {
            try {
                JsonNode node = ToolJson.MAPPER.readTree(candidate);
                return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
            } catch (JsonProcessingException e) {
                return Optional.empty();
            }
        });
    }

    /** The text of the first {...} candidate: inside the first code fence if there is one, else anywhere. */
    private static Optional<String> objectText(String text) {
        Matcher fence = FENCE.matcher(text);
        Optional<String> fenced = fence.find() ? firstObject(fence.group(1)) : Optional.empty();
        return fenced.or(() -> firstObject(text));
    }

    /** Jackson's short description of why {@code json} does not parse. */
    private static String jsonError(String json) {
        try {
            ToolJson.MAPPER.readTree(json);
            return "not a JSON object";
        } catch (JsonProcessingException e) {
            return truncate(e.getOriginalMessage(), 200);
        }
    }

    /** The first balanced {...} in {@code text}, ignoring braces inside JSON strings. */
    private static Optional<String> firstObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return Optional.empty();
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return Optional.of(text.substring(start, i + 1));
            }
        }
        return Optional.empty();
    }

    // ---- running calls ----

    /**
     * Validates the arguments and runs the tool. Never throws for bad input or tool failures.
     *
     * @param call the tool call
     * @return the compact JSON result, or an error explaining what went wrong
     */
    public ToolResult execute(ToolCall call) {
        Objects.requireNonNull(call, "call");
        executions.incrementAndGet();
        Tool tool = tools.get(call.tool());
        if (tool == null) {
            return ToolResult.error(call.tool(), "Unknown tool '" + call.tool() + "'. Available tools: "
                    + String.join(", ", tools.keySet()));
        }
        ObjectNode arguments;
        try {
            arguments = validate(tool.spec(), call.arguments());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(call.tool(), e.getMessage());
        }
        JsonNode result;
        try {
            result = tool.execute(arguments);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ToolResult.error(call.tool(), truncate(message, MAX_ERROR_LENGTH));
        }
        if (result == null) {
            return ToolResult.error(call.tool(), "The tool returned no result");
        }
        String json = result.toString();
        if (json.length() > MAX_RESULT_LENGTH) {
            return ToolResult.error(call.tool(), "Result too large (" + json.length()
                    + " characters); narrow the query (use a smaller limit or more specific arguments)");
        }
        return ToolResult.ok(call.tool(), json);
    }

    /** Checks the arguments against the spec and returns a cleaned copy (trimmed strings, nulls dropped). */
    private static ObjectNode validate(ToolSpec spec, JsonNode arguments) {
        Map<String, ToolParam> declared = new TreeMap<>();
        spec.params().forEach(param -> declared.put(param.name(), param));
        for (Iterator<String> names = arguments.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!declared.containsKey(name)) {
                throw new IllegalArgumentException("Unknown argument '" + name + "' for tool " + spec.name());
            }
        }
        ObjectNode clean = ToolJson.MAPPER.createObjectNode();
        for (ToolParam param : spec.params()) {
            JsonNode value = arguments.get(param.name());
            if (value == null || value.isNull()) {
                if (param.required()) {
                    throw new IllegalArgumentException("Missing required argument '" + param.name() + "'");
                }
                continue;
            }
            clean.set(param.name(), checkType(param, value));
        }
        return clean;
    }

    private static JsonNode checkType(ToolParam param, JsonNode value) {
        switch (param.type()) {
            case STRING -> {
                if (value.isTextual()) {
                    return new TextNode(value.asText().trim());
                }
            }
            case INTEGER -> {
                if (value.isIntegralNumber() && value.canConvertToInt()) {
                    return value;
                }
            }
            case NUMBER -> {
                if (value.isNumber() && Double.isFinite(value.asDouble())) {
                    return value;
                }
            }
            case BOOLEAN -> {
                if (value.isBoolean()) {
                    return value;
                }
            }
            case STRING_ARRAY -> {
                ArrayNode clean = cleanStringArray(value);
                if (clean != null) {
                    return clean;
                }
            }
        }
        throw new IllegalArgumentException("Argument '" + param.name() + "' must be " + describe(param.type()));
    }

    /** A trimmed copy of a string array, or null if it is not an array of at most 20 non-blank strings. */
    private static ArrayNode cleanStringArray(JsonNode value) {
        if (!value.isArray() || value.size() > MAX_ARRAY_ENTRIES) {
            return null;
        }
        ArrayNode clean = ToolJson.MAPPER.createArrayNode();
        for (JsonNode element : value) {
            if (!element.isTextual() || element.asText().isBlank()) {
                return null;
            }
            clean.add(element.asText().trim());
        }
        return clean;
    }

    private static String describe(ParamType type) {
        return switch (type) {
            case STRING -> "a string";
            case INTEGER -> "an integer";
            case NUMBER -> "a number";
            case BOOLEAN -> "a boolean (true or false)";
            case STRING_ARRAY -> "an array of at most " + MAX_ARRAY_ENTRIES + " non-blank strings";
        };
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
