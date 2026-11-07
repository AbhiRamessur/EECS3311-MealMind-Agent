package com.mealmind.gui.logic;

/**
 * One target progress bar of the Nutrition tab.
 *
 * @param label    "Calories", "Protein", "Carbs" or "Fat"
 * @param text     the formatter's line, with the real percentage, e.g. "Calories: 1414 of 2000 kcal per day (71%)"
 * @param fraction percent / 100, clamped to 0..1.5 (the bar itself shows at most 100%)
 * @param over     whether the average is at or above 110% of the target
 */
public record ProgressBarRow(String label, String text, double fraction, boolean over) {
}
