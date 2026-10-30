package com.mealmind.agent;

/**
 * What kind of step an {@link AgentEvent} reports.
 */
public enum AgentEventType {
    /** The model is being asked. */
    THINKING,
    /** A tool is about to run. */
    TOOL_CALL,
    /** A tool finished (or was refused). */
    TOOL_RESULT,
    /** The deterministic safety check ran. */
    VALIDATION,
    /** The model is asked to fix a malformed or implausible reply. */
    REPAIR,
    /** The model is asked to fix a reply that failed the safety check. */
    RETRY,
    /** An answer was accepted. */
    DONE,
    /** The run failed with an error. */
    ERROR
}
