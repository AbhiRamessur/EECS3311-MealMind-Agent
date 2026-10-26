package com.mealmind.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.command.AddPantryItemCommand;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import com.mealmind.shopping.ShoppingList;
import com.mealmind.shopping.ShoppingListGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationContextTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private static Meal recipeMeal(ApplicationContext ctx, String recipeId) {
        return new Meal(MealType.DINNER, null, List.of(ctx.recipeBook().get(recipeId).orElseThrow()));
    }

    @Test
    void createLoadsSamplesAndStartsEmpty() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);

        assertEquals(10, ctx.recipeBook().size());
        assertTrue(ctx.pantry().isEmpty());
        assertEquals(7, ctx.plan().getNumberOfDays());
        assertEquals(28, ctx.plan().getEmptySlots().size());
        assertFalse(ctx.history().canUndo());
        assertEquals(CLOCK, ctx.clock());
    }

    @Test
    void pantryChangesAreAutosavedAndRestored() {
        ApplicationContext first = ApplicationContext.create(dir, CLOCK);
        first.pantry().addItem(new PantryItem("Rice", 500, Unit.GRAM));
        first.pantry().addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));

        ApplicationContext second = ApplicationContext.create(dir, CLOCK);

        assertEquals(1500, second.pantry().getItem("rice").orElseThrow().getQuantity(), 1e-9);
    }

    @Test
    void loadingDoesNotTriggerSave() throws IOException {
        Path file = dir.resolve("pantry.json");
        String handWritten = "{\"items\":[{\"name\":\"Rice\",\"quantity\":500,\"unit\":\"g\"}]}";
        Files.writeString(file, handWritten);

        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);

        assertEquals(1, ctx.pantry().size());
        assertEquals(handWritten, Files.readString(file));
    }

    @Test
    void failedSaveIsReportedNotThrown() throws IOException {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        List<String> warnings = new ArrayList<>();
        ctx.setWarningSink(warnings::add);
        // A non-empty directory where pantry.json should be makes the save fail.
        Files.createDirectories(ctx.pantryFile());
        Files.writeString(ctx.pantryFile().resolve("blocker.txt"), "x");

        ctx.history().pushAndExecute(new AddPantryItemCommand(ctx.pantry(), new PantryItem("Rice", 1, Unit.KILOGRAM)));

        assertEquals(1, ctx.pantry().size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("not saved"), warnings.get(0));
    }

    @Test
    void newPlanDetachesOldGeneratorClearsHistoryAndTracksNewPlan() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        MealPlan oldPlan = ctx.plan();
        ShoppingListGenerator newGenerator;
        ctx.history().pushAndExecute(new AddPantryItemCommand(ctx.pantry(), new PantryItem("Salt", 1, Unit.KILOGRAM)));

        MealPlan newPlan = ctx.newPlan(3);
        newGenerator = ctx.generator();

        assertEquals(3, newPlan.getNumberOfDays());
        assertFalse(ctx.history().canUndo());
        assertEquals(0, oldPlan.observerCount());

        oldPlan.setMeal(1, MealType.DINNER, recipeMeal(ctx, "R101"));
        assertTrue(newGenerator.getCurrent().isEmpty());

        newPlan.setMeal(1, MealType.DINNER, recipeMeal(ctx, "R101"));
        ShoppingList list = newGenerator.getCurrent();
        assertEquals(7, list.size());
    }

    @Test
    void defaultDataDirUsesEnvironmentWhenSet() {
        assertEquals(Path.of("custom-dir"), ApplicationContext.defaultDataDir(Map.of("MEALMIND_DATA_DIR", "custom-dir")));
        assertEquals(Path.of("data"), ApplicationContext.defaultDataDir(Map.of()));
        assertEquals(Path.of("data"), ApplicationContext.defaultDataDir(Map.of("MEALMIND_DATA_DIR", "  ")));
    }
}
