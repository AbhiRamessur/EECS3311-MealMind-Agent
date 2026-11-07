package com.mealmind.gui.logic;

import java.util.List;

/**
 * The Plan tab's calendar grid.
 *
 * @param days         the plan length
 * @param rows         one row per day (unmodifiable)
 * @param totalsText   the CLI's "Plan totals: ..." line
 * @param warnings     advisory messages (the CLI prints them as "Advisory:"/"Warning:"), unmodifiable
 * @param safetyIssues safety messages (the CLI prints them as "SAFETY:"), unmodifiable
 */
public record PlanGrid(int days, List<PlanDayRow> rows, String totalsText, List<String> warnings, List<String> safetyIssues) {

    /** Copies the lists. */
    public PlanGrid {
        rows = List.copyOf(rows);
        warnings = List.copyOf(warnings);
        safetyIssues = List.copyOf(safetyIssues);
    }
}
