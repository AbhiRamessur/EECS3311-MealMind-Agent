package com.mealmind.substitution;

import java.util.Locale;

/**
 * Why the user wants to replace an ingredient (Stage 1 F07: Allergy, Missing, Preference).
 */
public enum SubstitutionReason {

    /** The user must avoid the ingredient. */
    ALLERGY("Allergy"),
    /** The user does not have the ingredient. */
    MISSING("Missing"),
    /** The user would rather not use the ingredient. */
    PREFERENCE("Preference");

    private final String displayName;

    SubstitutionReason(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable name.
     *
     * @return e.g. "Allergy"
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a reason, ignoring case and surrounding whitespace.
     *
     * @param text e.g. " Allergy "
     * @return the reason
     * @throws IllegalArgumentException if the text is not a known reason (also for null)
     */
    public static SubstitutionReason parse(String text) {
        String wanted = text == null ? "" : text.trim();
        for (SubstitutionReason reason : values()) {
            if (reason.name().equals(wanted.toUpperCase(Locale.ROOT))) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown reason '" + wanted + "'. Use: allergy, missing, preference");
    }
}
