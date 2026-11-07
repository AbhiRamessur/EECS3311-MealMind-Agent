package com.mealmind.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NutritionFormatterTest {

    private static NutritionSummary sample() {
        return new NutritionSummary(
                List.of(new DayNutrition(1, new Macros(1200, 75, 105, 31, 8), 2), new DayNutrition(2, new Macros(400, 20, 60, 10, 2), 1)),
                new Macros(1600, 95, 165, 41, 10), new Macros(800, 47.5, 82.5, 20.5, 5),
                List.of(TargetProgress.of("Calories", 800, 2000), TargetProgress.of("Protein", 47.5, 100)),
                List.of("Mystery sauce"), 3, 8);
    }

    @Test
    void formatsTheWholeSummary() {
        assertEquals("""
                Nutrition summary
                Day 1: 2 meals, 1200 kcal, P 75.0 g, C 105.0 g, F 31.0 g, $8.00
                Day 2: 1 meal, 400 kcal, P 20.0 g, C 60.0 g, F 10.0 g, $2.00
                Totals (3 meals planned, 8 slots): 1600 kcal, P 95.0 g, C 165.0 g, F 41.0 g, $10.00
                Daily average (2 days with meals): 800 kcal, P 47.5 g, C 82.5 g, F 20.5 g, $5.00
                Calories: 800 of 2000 kcal per day (40%)
                Protein: 47.5 of 100.0 g per day (48%)
                Nutritional data unavailable for: Mystery sauce (excluded from exact figures: they may be zero or missing)
                """, NutritionFormatter.toText(sample()));
    }

    @Test
    void emptyPlanIsOneLine() {
        NutritionSummary empty = new NutritionSummary(List.of(), Macros.ZERO, Macros.ZERO, List.of(), List.of(), 0, 28);

        assertEquals("No meals planned; nothing to analyze.\n", NutritionFormatter.toText(empty));
    }

    @Test
    void roundsCaloriesToWholeNumbersGramsToOneDecimalAndMoneyToTwoAsciiOnly() {
        NutritionSummary odd = new NutritionSummary(List.of(new DayNutrition(1, new Macros(1234.6, 12.36, 0.04, 9.96, 3.456), 1)),
                new Macros(1234.6, 12.36, 0.04, 9.96, 3.456), new Macros(1234.6, 12.36, 0.04, 9.96, 3.456),
                List.of(), List.of(), 1, 4);

        String text = NutritionFormatter.toText(odd);

        assertTrue(text.contains("Day 1: 1 meal, 1235 kcal, P 12.4 g, C 0.0 g, F 10.0 g, $3.46\n"), text);
        assertFalse(text.contains("unavailable"));
        assertFalse(text.contains("\r"));
        assertTrue(text.chars().allMatch(c -> c < 128), text);
    }
}
