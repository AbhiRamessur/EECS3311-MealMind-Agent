package com.mealmind.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A deterministic, read-only operation the agent can ask for, such as searching recipes or listing
 * the pantry.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #execute} receives arguments already validated against {@link #spec()} by
 *       {@link ToolManager} (types checked, strings trimmed, unknown keys rejected).</li>
 *   <li>For a semantic problem (an out-of-range value, an unknown id) a tool throws
 *       {@link IllegalArgumentException} or {@link java.util.NoSuchElementException} with a clear
 *       message the language model can act on.</li>
 *   <li>Tools never change application state, and for a fixed state and clock they always return
 *       the same result.</li>
 * </ul>
 */
public interface Tool {

    /**
     * Returns the tool's declaration.
     *
     * @return the spec
     */
    ToolSpec spec();

    /**
     * Runs the tool.
     *
     * @param arguments a JSON object of validated arguments (absent optional arguments are missing)
     * @return the result as JSON
     */
    JsonNode execute(JsonNode arguments);
}
