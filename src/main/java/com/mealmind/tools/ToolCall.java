package com.mealmind.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A request to run one tool, as parsed from model output or typed in the CLI.
 *
 * @param tool      the tool name
 * @param arguments a JSON object of arguments; an empty object when there are none
 */
public record ToolCall(String tool, JsonNode arguments) {

    /**
     * Validates the call; a null {@code arguments} becomes an empty object.
     *
     * @throws IllegalArgumentException if {@code tool} is blank or {@code arguments} is not a JSON object
     */
    public ToolCall {
        if (tool == null || tool.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        tool = tool.trim();
        if (arguments == null || arguments.isNull() || arguments.isMissingNode()) {
            arguments = ToolJson.MAPPER.createObjectNode();
        }
        if (!arguments.isObject()) {
            throw new IllegalArgumentException("Tool arguments must be a JSON object");
        }
    }
}
