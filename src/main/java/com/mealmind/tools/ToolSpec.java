package com.mealmind.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The declaration of a tool: its name, what it does and its arguments. {@link ToolManager} uses it
 * to validate calls and to describe the tool to the language model.
 *
 * @param name        snake_case name, e.g. {@code recipe_search}
 * @param description what the tool returns, written for the language model
 * @param params      the arguments (copied, unmodifiable, unique names)
 */
public record ToolSpec(String name, String description, List<ToolParam> params) {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{2,40}$");

    /**
     * Validates and copies the spec.
     *
     * @throws IllegalArgumentException if the name is not snake_case (3 to 41 characters), the
     *                                  description is blank, or two parameters share a name
     */
    public ToolSpec {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid tool name '" + name + "'");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description must not be blank");
        }
        params = List.copyOf(params == null ? List.of() : params);
        Set<String> seen = new HashSet<>();
        for (ToolParam param : params) {
            if (!seen.add(param.name())) {
                throw new IllegalArgumentException("Duplicate parameter '" + param.name() + "' in tool " + name);
            }
        }
    }

    /**
     * Returns the JSON Schema description of this tool:
     * {@code {"name", "description", "parameters": {"type": "object", "properties": {...},
     * "required": [...], "additionalProperties": false}}}. {@code required} is omitted when no
     * parameter is required; string-array properties include {@code "items": {"type": "string"}}.
     *
     * @return a new JSON object
     */
    public ObjectNode toJsonSchema() {
        ObjectNode schema = ToolJson.MAPPER.createObjectNode();
        schema.put("name", name);
        schema.put("description", description);
        ObjectNode parameters = schema.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        for (ToolParam param : params) {
            ObjectNode property = properties.putObject(param.name());
            property.put("type", param.type().jsonType());
            if (param.type() == ParamType.STRING_ARRAY) {
                property.putObject("items").put("type", "string");
            }
            property.put("description", param.description());
        }
        List<String> required = params.stream().filter(ToolParam::required).map(ToolParam::name).toList();
        if (!required.isEmpty()) {
            ArrayNode requiredNode = parameters.putArray("required");
            required.forEach(requiredNode::add);
        }
        parameters.put("additionalProperties", false);
        return schema;
    }

    /**
     * Returns a one-line signature, e.g. {@code recipe_search(query?: string, limit?: integer)}, where
     * {@code ?} marks an optional argument.
     *
     * @return the signature
     */
    public String signature() {
        return name + params.stream()
                .map(p -> p.name() + (p.required() ? "" : "?") + ": " + p.type().jsonType())
                .collect(Collectors.joining(", ", "(", ")"));
    }
}
