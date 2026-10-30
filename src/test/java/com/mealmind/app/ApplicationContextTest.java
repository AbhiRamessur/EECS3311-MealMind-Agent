package com.mealmind.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealmind.command.AddPantryItemCommand;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import com.mealmind.profile.DietType;
import com.mealmind.shopping.ShoppingList;
import com.mealmind.shopping.ShoppingListGenerator;
import com.mealmind.tools.ToolCall;
import com.mealmind.tools.ToolResult;
import com.mealmind.tools.ToolSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    void profileIsAutosavedAndRestored() {
        ApplicationContext first = ApplicationContext.create(dir, CLOCK);
        first.profile().setDiet(DietType.VEGAN);
        first.profile().addAllergen("Peanut");
        first.profile().setCalorieTarget(2100);

        ApplicationContext second = ApplicationContext.create(dir, CLOCK);

        assertEquals(DietType.VEGAN, second.profile().getDiet());
        assertEquals(Set.of("peanut"), second.profile().getAllergens());
        assertEquals(Optional.of(2100), second.profile().getCalorieTarget());
        assertTrue(Files.exists(second.profileFile()));
    }

    @Test
    void loadingProfileDoesNotTriggerSave() throws IOException {
        Path file = dir.resolve("profile.json");
        String handWritten = "{\"diet\":\"vegetarian\",\"allergens\":[\"Sesame\"],\"householdServings\":2}";
        Files.writeString(file, handWritten);

        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);

        assertEquals(DietType.VEGETARIAN, ctx.profile().getDiet());
        assertEquals(Set.of("sesame"), ctx.profile().getAllergens());
        assertEquals(handWritten, Files.readString(file));
    }

    @Test
    void failedProfileSaveIsReportedNotThrown() throws IOException {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        List<String> warnings = new ArrayList<>();
        ctx.setWarningSink(warnings::add);
        Files.createDirectories(ctx.profileFile());
        Files.writeString(ctx.profileFile().resolve("blocker.txt"), "x");

        ctx.profile().setDiet(DietType.VEGAN);

        assertEquals(DietType.VEGAN, ctx.profile().getDiet());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("profile changes were not saved"), warnings.get(0));
    }

    @Test
    void validatorChecksAgainstTheContextProfile() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        ctx.profile().setDiet(DietType.VEGETARIAN);

        assertFalse(ctx.validator().isSafe(ctx.recipeBook().get("R101").orElseThrow()));
        assertTrue(ctx.validator().isSafe(ctx.recipeBook().get("R103").orElseThrow()));
    }

    @Test
    void defaultDataDirUsesEnvironmentWhenSet() {
        assertEquals(Path.of("custom-dir"), ApplicationContext.defaultDataDir(Map.of("MEALMIND_DATA_DIR", "custom-dir")));
        assertEquals(Path.of("data"), ApplicationContext.defaultDataDir(Map.of()));
        assertEquals(Path.of("data"), ApplicationContext.defaultDataDir(Map.of("MEALMIND_DATA_DIR", "  ")));
    }

    @Test
    void toolsAndMemoryAreWiredAndFollowNewPlan() throws Exception {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        ObjectMapper json = new ObjectMapper();

        assertEquals(List.of("check_safety", "pantry_query", "plan_show", "recipe_search", "shopping_list"),
                ctx.tools().specs().stream().map(ToolSpec::name).toList());
        assertEquals(0, ctx.memory().size());
        assertEquals(List.of(), ctx.memory().facts());

        ctx.newPlan(3);
        ctx.plan().setMeal(1, MealType.DINNER, recipeMeal(ctx, "R101"));

        ToolResult plan = ctx.tools().execute(
                new ToolCall("plan_show", json.createObjectNode()));
        assertEquals(3, json.readTree(plan.json()).get("days").asInt());
        ToolResult list = ctx.tools().execute(
                new ToolCall("shopping_list", json.createObjectNode()));
        assertEquals(7, json.readTree(list.json()).get("count").asInt());
    }
}
