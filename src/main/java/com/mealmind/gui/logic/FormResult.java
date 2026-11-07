package com.mealmind.gui.logic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of parsing a form: a value, or every problem found.
 *
 * @param value  the parsed value when valid
 * @param errors the problems (unmodifiable); empty when valid
 * @param <T>    the value type
 */
public record FormResult<T>(Optional<T> value, List<FieldError> errors) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if a value and errors are both present, or neither
     */
    public FormResult {
        Objects.requireNonNull(value, "value");
        errors = List.copyOf(errors);
        if (value.isPresent() == !errors.isEmpty()) {
            throw new IllegalArgumentException("A form result has either a value or errors");
        }
    }

    /**
     * A valid result.
     *
     * @param value the value
     * @param <T>   the value type
     * @return the result
     */
    public static <T> FormResult<T> ok(T value) {
        return new FormResult<>(Optional.of(value), List.of());
    }

    /**
     * An invalid result.
     *
     * @param errors at least one problem
     * @param <T>    the value type
     * @return the result
     */
    public static <T> FormResult<T> failed(List<FieldError> errors) {
        return new FormResult<>(Optional.empty(), errors);
    }

    /**
     * Returns whether the form was valid.
     *
     * @return {@code true} if there are no errors
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
}
