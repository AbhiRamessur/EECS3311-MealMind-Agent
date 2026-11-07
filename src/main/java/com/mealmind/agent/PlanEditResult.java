package com.mealmind.agent;

import com.mealmind.llm.TokenUsage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one natural-language plan edit (F06), with the agent counters for the CLI stats line.
 *
 * @param outcome            applied, clarification or refused
 * @param message            what happened, or the clarifying question; non-blank
 * @param detail             advisory warnings (APPLIED) or safety violations (REFUSED); unmodifiable
 * @param commandDescription the executed command's description, present only when APPLIED
 * @param llmCalls           model calls made
 * @param toolSteps          tool calls requested
 * @param usage              tokens used
 */
public record PlanEditResult(EditOutcome outcome, String message, List<String> detail,
                             Optional<String> commandDescription, int llmCalls, int toolSteps, TokenUsage usage) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException     if a reference is null
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public PlanEditResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }
        detail = List.copyOf(detail);
        Objects.requireNonNull(commandDescription, "commandDescription");
        Objects.requireNonNull(usage, "usage");
    }
}
