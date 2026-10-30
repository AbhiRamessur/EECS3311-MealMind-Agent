package com.mealmind.profile;

import java.util.Locale;

/**
 * The diet a user follows. Each one (except {@link #OMNIVORE}) excludes certain foods; the exclusion
 * lists live in the validator.
 */
public enum DietType {
    OMNIVORE("Omnivore"),
    VEGETARIAN("Vegetarian"),
    VEGAN("Vegan"),
    PESCATARIAN("Pescatarian");

    private final String displayName;

    DietType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns a human-readable name, e.g. {@code "Vegan"}.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a diet type, ignoring case and surrounding whitespace.
     *
     * @param text the text to parse, e.g. {@code " Vegan "}
     * @return the matching diet type
     * @throws IllegalArgumentException if {@code text} is null, blank or not a known diet
     */
    public static DietType parse(String text) {
        if (text != null && !text.isBlank()) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // fall through to the error below
            }
        }
        throw new IllegalArgumentException("Unknown diet '" + (text == null ? "" : text.trim())
                + "'. Use one of: omnivore, vegetarian, vegan, pescatarian");
    }
}
