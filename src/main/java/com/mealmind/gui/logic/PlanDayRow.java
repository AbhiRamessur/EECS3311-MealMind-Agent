package com.mealmind.gui.logic;

import java.util.List;

/**
 * One day of the plan grid.
 *
 * @param day           the day number, from 1
 * @param dayLabel      e.g. "Day 2 (Tuesday)"
 * @param cells         the four slots in {@code MealType} order (unmodifiable)
 * @param dayTotalsText e.g. "842 kcal | $3.77", or "" for a day without meals
 */
public record PlanDayRow(int day, String dayLabel, List<PlanCell> cells, String dayTotalsText) {

    /** Copies the cells. */
    public PlanDayRow {
        cells = List.copyOf(cells);
    }
}
