package com.mealmind.validation;

import java.util.Objects;

/**
 * One broken rule found by the {@link ConstraintValidator}.
 *
 * @param severity whether it blocks ({@link Severity#SAFETY}) or only warns ({@link Severity#ADVISORY})
 * @param type     which rule was broken
 * @param subject  what it is about: an ingredient name, {@code "Day N"} or {@code "Plan"}
 * @param message  a complete sentence for the user
 */
public record Violation(Severity severity, ViolationType type, String subject, String message) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException     if any field is null
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public Violation {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Violation message must not be blank");
        }
    }
}
