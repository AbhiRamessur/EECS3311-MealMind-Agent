package com.mealmind.nutrition;

import java.util.Locale;
import java.util.Objects;

/**
 * Formats a {@link NutritionSummary} as plain ASCII text with {@code "\n"} line endings, for example:
 * <pre>
 * Nutrition summary
 * Day 1: 2 meals, 1414 kcal, P 116.1 g, C 167.0 g, F 27.9 g, $8.05
 * Totals (2 meals planned, 28 slots): 1414 kcal, P 116.1 g, C 167.0 g, F 27.9 g, $8.05
 * Daily average (1 day with meals): 1414 kcal, P 116.1 g, C 167.0 g, F 27.9 g, $8.05
 * Calories: 1414 of 2000 kcal per day (71%)
 * </pre>
 * Calories are whole numbers, grams have 1 decimal and money 2 (Locale.ROOT).
 */
public final class NutritionFormatter {

    private NutritionFormatter() {
    }

    /**
     * Formats a summary.
     *
     * @param summary the summary
     * @return the text, ending with a line break
     * @throws NullPointerException if {@code summary} is null
     */
    public static String toText(NutritionSummary summary) {
        Objects.requireNonNull(summary, "summary");
        if (summary.mealsPlanned() == 0) {
            return "No meals planned; nothing to analyze.\n";
        }
        StringBuilder text = new StringBuilder("Nutrition summary\n");
        for (DayNutrition day : summary.days()) {
            text.append("Day ").append(day.day()).append(": ").append(count(day.filledSlots(), "meal")).append(", ")
                    .append(macros(day.macros())).append('\n');
        }
        text.append("Totals (").append(count(summary.mealsPlanned(), "meal")).append(" planned, ")
                .append(summary.slotsTotal()).append(" slots): ").append(macros(summary.totals())).append('\n');
        text.append("Daily average (").append(count(summary.days().size(), "day")).append(" with meals): ")
                .append(macros(summary.dailyAverage())).append('\n');
        for (TargetProgress target : summary.progress()) {
            boolean calories = target.label().equals("Calories");
            String unit = calories ? " kcal" : " g";
            text.append(target.label()).append(": ")
                    .append(calories ? whole(target.actual()) : grams(target.actual())).append(" of ")
                    .append(calories ? whole(target.target()) : grams(target.target())).append(unit)
                    .append(" per day (").append(target.percent()).append("%)\n");
        }
        if (!summary.unknownIngredients().isEmpty()) {
            text.append("Nutritional data unavailable for: ").append(String.join(", ", summary.unknownIngredients()))
                    .append(" (excluded from exact figures: they may be zero or missing)\n");
        }
        return text.toString();
    }

    private static String macros(Macros m) {
        return whole(m.calories()) + " kcal, P " + grams(m.proteinG()) + " g, C " + grams(m.carbsG())
                + " g, F " + grams(m.fatG()) + " g, $" + String.format(Locale.ROOT, "%.2f", m.cost());
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private static String whole(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String grams(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
