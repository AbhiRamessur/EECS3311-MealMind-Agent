package com.mealmind.gui.logic;

import com.mealmind.nutrition.NutritionFormatter;
import com.mealmind.nutrition.NutritionSummary;
import com.mealmind.nutrition.TargetProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the Nutrition tab's content from a {@link NutritionSummary}. Every text line is taken from
 * {@link NutritionFormatter#toText}, so the GUI shows exactly what {@code plan nutrition} prints; only the bar
 * fractions are added.
 */
public final class NutritionView {

    /** The bar fraction never exceeds this (150%). */
    public static final double MAX_FRACTION = 1.5;
    /** At or above this percentage a bar is styled as over target (the validator's 110% calorie rule). */
    public static final int OVER_PERCENT = 110;
    /** Shown when no meals are planned, as the CLI prints it. */
    public static final String EMPTY_TEXT = "No meals planned; nothing to analyze.";

    private NutritionView() {
    }

    /**
     * Builds the view model.
     *
     * @param summary the computed summary
     * @return the view model
     * @throws NullPointerException if {@code summary} is null
     */
    public static NutritionViewModel of(NutritionSummary summary) {
        Objects.requireNonNull(summary, "summary");
        if (summary.mealsPlanned() == 0) {
            return new NutritionViewModel(List.of(), "", "", List.of(), "", true);
        }
        List<String> lines = NutritionFormatter.toText(summary).lines().toList();
        List<String> days = lines.stream().filter(line -> line.startsWith("Day ")).toList();
        String totals = first(lines, "Totals ");
        String average = first(lines, "Daily average ");
        String unknown = first(lines, "Nutritional data unavailable");
        List<ProgressBarRow> bars = new ArrayList<>();
        for (TargetProgress target : summary.progress()) {
            double fraction = Math.max(0, Math.min(target.percent() / 100.0, MAX_FRACTION));
            bars.add(new ProgressBarRow(target.label(), first(lines, target.label() + ": "), fraction,
                    target.percent() >= OVER_PERCENT));
        }
        return new NutritionViewModel(days, totals, average, bars, unknown, false);
    }

    private static String first(List<String> lines, String prefix) {
        return lines.stream().filter(line -> line.startsWith(prefix)).findFirst().orElse("");
    }
}
