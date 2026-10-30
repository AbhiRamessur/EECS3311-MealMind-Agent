package com.mealmind.agent;

/**
 * How an agent run ended.
 */
public enum RunStatus {
    /** The answer passed every check. */
    ACCEPTED,
    /** The model kept producing answers that break the safety rules; the last one is returned flagged. */
    UNSAFE,
    /** No usable answer was produced within the budgets. */
    FAILED
}
