package com.mealmind.gui.logic;

import java.util.List;

/**
 * What the Nutrition tab shows.
 *
 * @param dayLines    the formatter's "Day N: ..." lines (unmodifiable)
 * @param totalsLine  the formatter's totals line, or "" when empty
 * @param averageLine the formatter's daily-average line, or "" when empty
 * @param progress    one bar per target set in the profile (unmodifiable)
 * @param unknownLine the "Nutritional data unavailable for: ..." line, or ""
 * @param empty       whether no meals are planned (the tab then shows "No meals planned; nothing to analyze.")
 */
public record NutritionViewModel(List<String> dayLines, String totalsLine, String averageLine, List<ProgressBarRow> progress,
                                 String unknownLine, boolean empty) {

    /** Copies the lists. */
    public NutritionViewModel {
        dayLines = List.copyOf(dayLines);
        progress = List.copyOf(progress);
    }
}
