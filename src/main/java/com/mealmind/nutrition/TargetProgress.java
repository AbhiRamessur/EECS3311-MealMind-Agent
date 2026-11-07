package com.mealmind.nutrition;

import java.util.Objects;

/**
 * How a daily average compares with one of the profile's targets.
 *
 * @param label   "Calories", "Protein", "Carbs" or "Fat"
 * @param actual  the daily average
 * @param target  the profile's daily target, above 0
 * @param percent {@code actual / target * 100}, rounded to the nearest int
 */
public record TargetProgress(String label, double actual, double target, int percent) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if {@code label} is null
     */
    public TargetProgress {
        Objects.requireNonNull(label, "label");
    }

    /**
     * Creates a progress entry, computing the percentage.
     *
     * @param label  the target's label
     * @param actual the daily average
     * @param target the daily target, above 0
     * @return the entry
     */
    public static TargetProgress of(String label, double actual, double target) {
        return new TargetProgress(label, actual, target, (int) Math.round(actual / target * 100));
    }
}
