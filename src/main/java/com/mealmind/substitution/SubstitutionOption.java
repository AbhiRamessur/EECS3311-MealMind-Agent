package com.mealmind.substitution;

import java.util.Objects;

/**
 * One suggested replacement. The nutrition and cost are <strong>totals</strong> for the replacement
 * quantity, which is {@code ratio} times the original quantity in the <em>same</em> unit as the original.
 * They are AI estimates that passed a plausibility check.
 *
 * @param name     the replacement ingredient, 1 to {@value #MAX_NAME} characters after trimming
 * @param ratio    replacement amount relative to the original, {@value #MIN_RATIO} to {@value #MAX_RATIO}
 * @param note     the culinary impact, at most {@value #MAX_NOTE} characters (may be empty)
 * @param calories total kcal for the replacement quantity
 * @param proteinG total protein in grams
 * @param carbsG   total carbohydrates in grams
 * @param fatG     total fat in grams
 * @param cost     total cost
 * @param inPantry whether the pantry already holds enough of it
 */
public record SubstitutionOption(String name, double ratio, String note, double calories, double proteinG,
                                 double carbsG, double fatG, double cost, boolean inPantry) {

    /** The longest accepted name. */
    public static final int MAX_NAME = 60;
    /** The longest accepted note. */
    public static final int MAX_NOTE = 140;
    /** The smallest accepted ratio. */
    public static final double MIN_RATIO = 0.25;
    /** The largest accepted ratio. */
    public static final double MAX_RATIO = 4.0;

    /**
     * Validates the fields and trims the texts.
     *
     * @throws IllegalArgumentException if a value is out of range
     * @throws NullPointerException     if {@code name} or {@code note} is null
     */
    public SubstitutionOption {
        name = Objects.requireNonNull(name, "name").trim();
        note = Objects.requireNonNull(note, "note").trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Substitute name must be 1 to " + MAX_NAME + " characters");
        }
        if (!Double.isFinite(ratio) || ratio < MIN_RATIO || ratio > MAX_RATIO) {
            throw new IllegalArgumentException("Ratio must be from " + MIN_RATIO + " to " + MAX_RATIO + ", got " + ratio);
        }
        if (note.length() > MAX_NOTE) {
            throw new IllegalArgumentException("Note must be at most " + MAX_NOTE + " characters");
        }
        requireAmount(calories, "calories");
        requireAmount(proteinG, "proteinG");
        requireAmount(carbsG, "carbsG");
        requireAmount(fatG, "fatG");
        requireAmount(cost, "cost");
    }

    private static void requireAmount(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be a finite number >= 0, got " + value);
        }
    }
}
