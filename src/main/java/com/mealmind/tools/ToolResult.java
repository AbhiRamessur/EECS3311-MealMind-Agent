package com.mealmind.tools;

/**
 * The outcome of a tool call. This is what the agent (next commit) feeds back to the language model
 * as an observation: either compact JSON or an error message the model can use to correct itself.
 *
 * @param ok    whether the tool succeeded
 * @param tool  the tool that was called (as requested)
 * @param json  the compact JSON result when {@code ok}, otherwise null
 * @param error the error message when not {@code ok}, otherwise null
 */
public record ToolResult(boolean ok, String tool, String json, String error) {

    /**
     * Creates a successful result.
     *
     * @param tool the tool name
     * @param json compact JSON text
     * @return the result
     */
    public static ToolResult ok(String tool, String json) {
        return new ToolResult(true, tool, json, null);
    }

    /**
     * Creates a failed result.
     *
     * @param tool    the tool name
     * @param message a one-line explanation
     * @return the result
     */
    public static ToolResult error(String tool, String message) {
        return new ToolResult(false, tool, null, message);
    }
}
