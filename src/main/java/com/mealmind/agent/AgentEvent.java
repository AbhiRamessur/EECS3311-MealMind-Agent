package com.mealmind.agent;

import java.util.Objects;

/**
 * One line of the agent's activity log (the Stage 1 "Agent Activity" panel: printed live in the CLI
 * now, shown in the GUI later). The message is cleaned on creation: whitespace (including newlines
 * and tabs) collapses to single spaces, and anything over 200 characters is cut and ends with "…",
 * so raw model output can never flood the log.
 *
 * @param type    what kind of step this is
 * @param message a one-line description
 */
public record AgentEvent(AgentEventType type, String message) {

    /** Longest message, in characters. */
    public static final int MAX_LENGTH = 200;

    /**
     * Validates and cleans the event.
     *
     * @throws NullPointerException     if {@code type} is null
     * @throws IllegalArgumentException if {@code message} is null or blank
     */
    public AgentEvent {
        Objects.requireNonNull(type, "type");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Event message must not be blank");
        }
        message = message.replaceAll("\\s+", " ").trim();
        if (message.length() > MAX_LENGTH) {
            message = message.substring(0, MAX_LENGTH - 1) + "…";
        }
    }
}
