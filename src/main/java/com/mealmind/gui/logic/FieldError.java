package com.mealmind.gui.logic;

import java.util.Objects;

/**
 * A problem with one form field.
 *
 * @param field   the field name, e.g. "calories", or "form" when no single field is at fault
 * @param message the message to show
 */
public record FieldError(String field, String message) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if an argument is null
     */
    public FieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
