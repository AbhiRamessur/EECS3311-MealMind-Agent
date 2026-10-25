package com.mealmind.model;

import java.util.Objects;

/**
 * Describes one change to an {@link ObservableSubject}, passed to every {@link ModelObserver}.
 *
 * @param source      the subject that changed
 * @param description a short human-readable summary, e.g. {@code "Day 2 DINNER set to Pasta"}
 */
public record ChangeEvent(ObservableSubject source, String description) {

    /**
     * Validates the event.
     *
     * @throws NullPointerException     if {@code source} is null
     * @throws IllegalArgumentException if {@code description} is null or blank
     */
    public ChangeEvent {
        Objects.requireNonNull(source, "source");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Change description must not be blank");
        }
    }
}
