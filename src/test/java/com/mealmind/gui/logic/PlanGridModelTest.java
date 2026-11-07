package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.agent.PlanMeals;
import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
import com.mealmind.model.MealType;
import com.mealmind.model.RecipeScaler;
import com.mealmind.profile.DietType;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanGridModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private ApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.create(dir, CLOCK, warning -> { });
    }

    private PlanGrid grid() {
        return PlanGridModel.of(ctx.plan(), ctx.validator());
    }

    private List<String> cli(String line) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new CLIHandler(ctx, new BufferedReader(new StringReader("")), new PrintStream(buffer, true, StandardCharsets.UTF_8))
                .handleLine(line);
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void emptyPlanHasFourEmptySlotsPerDayInEnumOrder() {
        PlanGrid grid = grid();

        assertEquals(7, grid.days());
        assertEquals(7, grid.rows().size());
        for (PlanDayRow row : grid.rows()) {
            assertEquals(Arrays.asList(MealType.values()), row.cells().stream().map(PlanCell::slot).toList());
            assertTrue(row.cells().stream().noneMatch(PlanCell::filled));
            assertTrue(row.cells().stream().allMatch(c -> c.text().equals("(empty)") && c.recipeId() == null));
            assertEquals("", row.dayTotalsText());
        }
        assertEquals(List.of(), grid.warnings());
    }

    @Test
    void weekdayLabels() {
        assertEquals(List.of("Day 1 (Monday)", "Day 2 (Tuesday)", "Day 3 (Wednesday)", "Day 4 (Thursday)", "Day 5 (Friday)",
                "Day 6 (Saturday)", "Day 7 (Sunday)"), grid().rows().stream().map(PlanDayRow::dayLabel).toList());
    }

    @Test
    void filledCellsTotalsAndTheCliWording() {
        ctx.plan().setMeal(2, MealType.DINNER, PlanMeals.forRecipe(MealType.DINNER,
                RecipeScaler.scale(ctx.recipeBook().get("R103").orElseThrow(), 2)));

        PlanGrid grid = grid();
        PlanCell cell = grid.rows().get(1).cells().get(MealType.DINNER.ordinal());
        List<String> show = cli("plan show");

        assertTrue(cell.filled());
        assertEquals("R103", cell.recipeId());
        assertEquals("Red Lentil Soup (2 servings)", cell.text());
        assertTrue(show.contains("  Dinner: " + cell.text()), show.toString());
        assertTrue(show.contains(grid.totalsText()), show.toString());
        assertTrue(grid.totalsText().startsWith("Plan totals: "));
        double kcal = ctx.plan().getDay(2).getCalories();
        assertEquals(String.format(java.util.Locale.ROOT, "%.0f kcal | $%.2f", kcal, ctx.plan().getDay(2).getCost()),
                grid.rows().get(1).dayTotalsText());
        assertEquals("", grid.rows().get(0).dayTotalsText());
    }

    @Test
    void advisoriesAndSafetyIssuesAreSplit() {
        ctx.plan().setMeal(1, MealType.DINNER, PlanMeals.forRecipe(MealType.DINNER, ctx.recipeBook().get("R101").orElseThrow()));
        ctx.profile().setCalorieTarget(800);
        ctx.profile().setDiet(DietType.VEGETARIAN);

        PlanGrid grid = grid();

        assertEquals(1, grid.warnings().size());
        assertTrue(grid.warnings().get(0).startsWith("Day 1: "), grid.warnings().toString());
        assertFalse(grid.safetyIssues().isEmpty());
        assertTrue(grid.safetyIssues().get(0).startsWith("Chicken breast is not allowed for a vegetarian diet"));
    }
}
