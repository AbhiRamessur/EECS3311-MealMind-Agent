package com.mealmind.agent;

/**
 * Unchecked exception for an agent run that produced no usable answer. The message is one line and
 * contains at most a short, truncated excerpt of model output.
 */
public class AgentException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     */
    public AgentException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message what went wrong
     * @param cause   the underlying exception
     */
    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
