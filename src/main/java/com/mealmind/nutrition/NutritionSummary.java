package com.mealmind.nutrition;

import java.util.List;
import java.util.Objects;

/**
 * The deterministic nutrition analysis of a plan (Stage 1 {@code NutritionSummary}).
 *
 * @param days               only the days that have at least one meal
 * @param totals             the whole plan's totals
 * @param dailyAverage       {@code totals} divided by the number of days with meals (zeros when none)
 * @param progress           the daily average against each target the profile sets
 * @param unknownIngredients names of ingredients without nutrition data (see {@link NutritionData}), sorted
 * @param mealsPlanned       filled slots across the plan
 * @param slotsTotal         days x 4
 */
public record NutritionSummary(List<DayNutrition> days, Macros totals, Macros dailyAverage,
                               List<TargetProgress> progress, List<String> unknownIngredients,
                               int mealsPlanned, int slotsTotal) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException if any field or element is null
     */
    public NutritionSummary {
        days = List.copyOf(days);
        Objects.requireNonNull(totals, "totals");
        Objects.requireNonNull(dailyAverage, "dailyAverage");
        progress = List.copyOf(progress);
        unknownIngredients = List.copyOf(unknownIngredients);
    }

    /**
     * Returns a copy with other unknown-ingredient names (used to sanitize names before a prompt).
     *
     * @param names the replacement names
     * @return the copy
     */
    public NutritionSummary withUnknownIngredients(List<String> names) {
        return new NutritionSummary(days, totals, dailyAverage, progress, names, mealsPlanned, slotsTotal);
    }
}
