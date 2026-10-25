package com.mealmind.model;

import java.util.Locale;

/**
 * The slots a day can hold, in the order they are eaten.
 */
public enum MealType {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    private final String displayName;

    MealType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns a human-readable name, e.g. {@code "Dinner"}.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a meal type, ignoring case and surrounding whitespace.
     *
     * @param text the text to parse, e.g. {@code "dinner"} or {@code " LUNCH "}
     * @return the matching meal type
     * @throws IllegalArgumentException if {@code text} is null, blank or not a known meal type
     */
    public static MealType parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Meal type must not be blank");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown meal type '" + text.trim()
                    + "'. Use one of: breakfast, lunch, dinner, snack");
        }
    }
}
