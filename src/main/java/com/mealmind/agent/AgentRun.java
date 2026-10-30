package com.mealmind.agent;

import com.mealmind.llm.TokenUsage;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one {@link AgentLoop} run, with the counters used for the CLI stats line.
 *
 * @param status        how the run ended
 * @param value         the answer: present for ACCEPTED and UNSAFE, empty for FAILED
 * @param message       a short explanation (may be empty)
 * @param llmCalls      model calls made
 * @param toolSteps     tool calls requested (including any refused for budget)
 * @param repairs       repair prompts sent for malformed or rejected replies
 * @param safetyRetries answers that failed the safety check
 * @param usage         tokens used over all model calls
 * @param <T>           the answer type
 */
public record AgentRun<T>(RunStatus status, Optional<T> value, String message, int llmCalls, int toolSteps,
                          int repairs, int safetyRetries, TokenUsage usage) {

    /**
     * Validates the run.
     *
     * @throws NullPointerException     if any reference is null
     * @throws IllegalArgumentException if the value's presence does not match the status
     */
    public AgentRun {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(usage, "usage");
        if (value.isPresent() == (status == RunStatus.FAILED)) {
            throw new IllegalArgumentException("A " + status + " run must " + (status == RunStatus.FAILED ? "not " : "")
                    + "carry a value");
        }
    }
}
