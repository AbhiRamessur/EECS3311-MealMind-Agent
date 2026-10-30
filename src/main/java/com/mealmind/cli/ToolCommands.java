package com.mealmind.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealmind.tools.ToolCall;
import com.mealmind.tools.ToolManager;
import com.mealmind.tools.ToolResult;
import com.mealmind.tools.ToolSpec;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Objects;

/**
 * The {@code tools} commands: list the agent's tools, print their JSON schemas, or call one by hand.
 * This is a developer and demo aid showing the deterministic tool layer without any AI; normally the
 * agent (a later commit) calls the tools.
 *
 * <p>It receives the raw text after {@code tools}, not tokens, because {@code tools call} takes JSON,
 * which the tokenizer would break by removing its double quotes.
 */
final class ToolCommands {

    static final String USAGE = "tools list | tools schema | tools call <name> [json]";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolManager tools;
    private final PrintStream out;

    ToolCommands(ToolManager tools, PrintStream out) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** Runs {@code tools <rest>}, where {@code rest} is the raw remainder of the line. */
    void handle(String rest) {
        String[] parts = rest.strip().split("\\s+", 2);
        String remainder = parts.length > 1 ? parts[1] : "";
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list();
            case "schema" -> out.println(pretty(tools.schemas()));
            case "call" -> call(remainder);
            default -> throw new UsageException(USAGE);
        }
    }

    private void list() {
        for (ToolSpec spec : tools.specs()) {
            out.println(spec.signature());
            out.println("    " + spec.description());
        }
    }

    /** {@code <name> [json]}: the JSON is everything after the name, or {} when absent. */
    private void call(String remainder) {
        String[] parts = remainder.strip().split("\\s+", 2);
        if (parts[0].isEmpty()) {
            throw new UsageException(USAGE);
        }
        String json = parts.length > 1 ? parts[1].strip() : "";
        JsonNode arguments;
        try {
            arguments = json.isEmpty() ? MAPPER.createObjectNode() : MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            arguments = null;
        }
        if (arguments == null || !arguments.isObject()) {
            out.println("Error: arguments must be a JSON object");
            return;
        }
        ToolResult result = tools.execute(new ToolCall(parts[0], arguments));
        if (!result.ok()) {
            out.println("Error: " + result.error());
            return;
        }
        try {
            out.println(pretty(MAPPER.readTree(result.json())));
        } catch (JsonProcessingException e) {
            out.println(result.json());
        }
    }

    private static String pretty(JsonNode node) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node.toString();
        }
    }
}
