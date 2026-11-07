package com.mealmind.nutrition;

import java.util.Objects;

/**
 * One day's totals.
 *
 * @param day         the day number, from 1
 * @param macros      the day's totals
 * @param filledSlots how many meals the day has
 */
public record DayNutrition(int day, Macros macros, int filledSlots) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if {@code macros} is null
     */
    public DayNutrition {
        Objects.requireNonNull(macros, "macros");
    }
}
