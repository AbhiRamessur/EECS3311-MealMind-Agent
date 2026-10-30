package com.mealmind.tools;

/**
 * Thrown when model output does not contain a usable tool call. The message is one line, written so
 * that it can be sent back to the model to ask for a corrected reply.
 */
public class ToolParseException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what is wrong with the reply
     */
    public ToolParseException(String message) {
        super(message);
    }
}
