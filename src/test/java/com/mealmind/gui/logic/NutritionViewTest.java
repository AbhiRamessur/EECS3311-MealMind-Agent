package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.nutrition.DayNutrition;
import com.mealmind.nutrition.Macros;
import com.mealmind.nutrition.NutritionFormatter;
import com.mealmind.nutrition.NutritionSummary;
import com.mealmind.nutrition.TargetProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

class NutritionViewTest {

    private static NutritionSummary summary(List<TargetProgress> progress, List<String> unknown) {
        return new NutritionSummary(
                List.of(new DayNutrition(1, new Macros(1200, 75, 105, 31, 8), 2), new DayNutrition(2, new Macros(400, 20, 60, 10, 2), 1)),
                new Macros(1600, 95, 165, 41, 10), new Macros(800, 47.5, 82.5, 20.5, 5), progress, unknown, 3, 8);
    }

    @Test
    void linesEqualTheFormatterOutput() {
        NutritionSummary summary = summary(List.of(TargetProgress.of("Calories", 800, 2000)), List.of("Mystery sauce"));
        List<String> formatter = NutritionFormatter.toText(summary).lines().toList();

        NutritionViewModel view = NutritionView.of(summary);

        assertFalse(view.empty());
        assertEquals(formatter.subList(1, 3), view.dayLines());
        assertEquals(formatter.get(3), view.totalsLine());
        assertEquals(formatter.get(4), view.averageLine());
        assertEquals(formatter.get(5), view.progress().get(0).text());
        assertEquals(formatter.get(6), view.unknownLine());
    }

    @Test
    void fractionsAreClampedButTheTextKeepsTheRealPercentage() {
        NutritionViewModel view = NutritionView.of(summary(List.of(TargetProgress.of("Calories", 800, 2000),
                TargetProgress.of("Protein", 47.5, 20), TargetProgress.of("Fat", 21, 20)), List.of()));

        ProgressBarRow calories = view.progress().get(0);
        assertEquals(0.4, calories.fraction(), 1e-9);
        assertFalse(calories.over());
        ProgressBarRow protein = view.progress().get(1);
        assertEquals(1.5, protein.fraction(), 1e-9);
        assertTrue(protein.over());
        assertTrue(protein.text().endsWith("(238%)"), protein.text());
        ProgressBarRow fat = view.progress().get(2);
        assertEquals(1.05, fat.fraction(), 1e-9);
        assertFalse(fat.over());
        assertEquals("", view.unknownLine());
    }

    @Test
    void emptyPlan() {
        NutritionViewModel view = NutritionView.of(new NutritionSummary(List.of(), Macros.ZERO, Macros.ZERO, List.of(), List.of(), 0, 28));

        assertTrue(view.empty());
        assertEquals(List.of(), view.dayLines());
        assertEquals(List.of(), view.progress());
        assertEquals(NutritionFormatter.toText(new NutritionSummary(List.of(), Macros.ZERO, Macros.ZERO, List.of(), List.of(), 0, 28)).strip(),
                NutritionView.EMPTY_TEXT);
    }
}
