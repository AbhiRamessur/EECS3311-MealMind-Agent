package com.mealmind.tools;

/**
 * The JSON type of a tool argument.
 */
public enum ParamType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    STRING_ARRAY("array");

    private final String jsonType;

    ParamType(String jsonType) {
        this.jsonType = jsonType;
    }

    /**
     * Returns the JSON Schema type name, e.g. {@code "integer"}.
     *
     * @return the type name used in tool schemas
     */
    public String jsonType() {
        return jsonType;
    }
}
