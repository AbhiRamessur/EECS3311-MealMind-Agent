package com.mealmind.tools;

import java.util.regex.Pattern;

/**
 * One declared argument of a tool.
 *
 * @param name        camelCase name, e.g. {@code maxPrepMinutes}
 * @param type        the JSON type
 * @param description what the argument means, written for the language model
 * @param required    whether the caller must supply it
 */
public record ToolParam(String name, ParamType type, String description, boolean required) {

    private static final Pattern NAME = Pattern.compile("^[a-z][A-Za-z0-9]{0,30}$");

    /**
     * Validates the parameter.
     *
     * @throws IllegalArgumentException if the name is not camelCase (1 to 31 characters), the type is
     *                                  null or the description is blank
     */
    public ToolParam {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid parameter name '" + name + "'");
        }
        if (type == null) {
            throw new IllegalArgumentException("Parameter type must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Parameter description must not be blank");
        }
    }

    /**
     * Creates a required parameter.
     *
     * @param name        the name
     * @param type        the type
     * @param description the description
     * @return the parameter
     */
    public static ToolParam required(String name, ParamType type, String description) {
        return new ToolParam(name, type, description, true);
    }

    /**
     * Creates an optional parameter.
     *
     * @param name        the name
     * @param type        the type
     * @param description the description
     * @return the parameter
     */
    public static ToolParam optional(String name, ParamType type, String description) {
        return new ToolParam(name, type, description, false);
    }
}
