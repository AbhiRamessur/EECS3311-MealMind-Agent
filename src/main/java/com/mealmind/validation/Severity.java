package com.mealmind.validation;

/**
 * How serious a {@link Violation} is.
 */
public enum Severity {
    /** An allergen or diet conflict: the meal must not be accepted. */
    SAFETY,
    /** A target or budget is exceeded: shown as a warning, never blocks. */
    ADVISORY
}
