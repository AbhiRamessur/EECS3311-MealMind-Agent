package com.mealmind.substitution;

import com.mealmind.llm.TokenUsage;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of asking the AI for substitutes.
 *
 * @param proposal  the safe options, or empty when no safe substitute was found
 * @param message   what to tell the user; non-blank
 * @param llmCalls  model calls made
 * @param toolSteps tool calls made
 * @param usage     tokens used
 */
public record SubstitutionResult(Optional<SubstitutionProposal> proposal, String message, int llmCalls, int toolSteps,
                                 TokenUsage usage) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException     if a reference is null
     * @throws IllegalArgumentException if the message is blank or a counter is negative
     */
    public SubstitutionResult {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(usage, "usage");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }
        if (llmCalls < 0 || toolSteps < 0) {
            throw new IllegalArgumentException("Counters must not be negative");
        }
    }
}
