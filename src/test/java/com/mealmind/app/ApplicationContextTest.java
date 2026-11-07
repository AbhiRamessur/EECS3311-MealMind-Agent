package com.mealmind.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealmind.command.AddPantryItemCommand;
import com.mealmind.command.RemoveRecipeCommand;
import com.mealmind.command.ScaleRecipeCommand;
import com.mealmind.llm.LLMClient;
import com.mealmind.llm.LlmErrorKind;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.TokenUsage;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import com.mealmind.persistence.RecipeRepository;
import com.mealmind.persistence.RepositoryException;
import com.mealmind.planning.PlanRequest;
import com.mealmind.planning.PlanResult;
import com.mealmind.profile.DietType;
import com.mealmind.recommend.RecommendationRequest;
import com.mealmind.recommend.RecommendationResult;
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

    @Test
    void agentUsesTheSharedMemoryAndTools() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        List<LlmRequest> requests = new ArrayList<>();
        ctx.llm().useClient(new LLMClient() {
            @Override
            public String providerName() {
                return "Recording";
            }

            @Override
            public String modelName() {
                return "fake";
            }

            @Override
            public LlmResponse complete(LlmRequest request) {
                requests.add(request);
                throw new LlmException(LlmErrorKind.TIMEOUT, "stop here", null);
            }
        });
        ctx.memory().remember("Prefers spicy food");

        assertThrows(LlmException.class, () -> ctx.agent().generateRecipe("dinner", null));

        String systemPrompt = requests.get(0).messages().get(0).content();
        assertTrue(systemPrompt.contains("- Prefers spicy food"), systemPrompt);
        assertTrue(systemPrompt.contains("recipe_search("), systemPrompt);
        assertTrue(ctx.agent().pendingRecipe().isEmpty());
    }

    // ---- F12: saved recipes, ratings and favourites ----

    /** A fake model that always answers with the same valid recipe draft. */
    private static final class OneRecipeClient implements LLMClient {
        private final String name;

        OneRecipeClient(String name) {
            this.name = name;
        }

        @Override
        public String providerName() {
            return "OneRecipe";
        }

        @Override
        public String modelName() {
            return "fake";
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            String ingredient = "{\"name\": \"Rice\", \"quantity\": 100, \"unit\": \"g\", \"calories\": 370, "
                    + "\"proteinG\": 20, \"carbsG\": 50, \"fatG\": 10, \"cost\": 1.5}";
            return new LlmResponse("{\"recipe\": {\"name\": \"" + name + "\", \"servings\": 2, \"prepMinutes\": 15, "
                    + "\"ingredients\": [" + ingredient + ", " + ingredient.replace("Rice", "Lemon") + "], "
                    + "\"instructions\": [\"Cook the rice.\", \"Add lemon.\"]}}", TokenUsage.ZERO, "fake");
        }
    }

    private static ApplicationContext withSavedRecipe(Path dir, List<String> warnings, String name) {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warnings::add);
        ctx.llm().useClient(new OneRecipeClient(name));
        ctx.agent().generateRecipe("something", null);
        ctx.agent().savePendingRecipe(ctx.history());
        return ctx;
    }

    @Test
    void userRecipesAndPreferencesAreRestored() {
        List<String> warnings = new ArrayList<>();
        ApplicationContext first = withSavedRecipe(dir, warnings, "Lemon Rice");
        first.preferences().rate("AI001", 5);
        first.preferences().setFavorite("AI001", true);

        ApplicationContext second = ApplicationContext.create(dir, CLOCK, warnings::add);

        assertEquals("Lemon Rice", second.recipeBook().get("AI001").orElseThrow().getName());
        assertEquals(Optional.of(5), second.preferences().getRating("AI001"));
        assertTrue(second.preferences().isFavorite("AI001"));
        assertEquals(11, second.recipeBook().size());
        assertEquals(List.of(), warnings);
    }

    @Test
    void loadingUserFilesDoesNotTriggerSaves() throws IOException {
        withSavedRecipe(dir, new ArrayList<>(), "Lemon Rice").preferences().rate("R101", 4);
        String recipes = Files.readString(dir.resolve("user-recipes.json"));
        String ratings = Files.readString(dir.resolve("ratings.json"));

        ApplicationContext.create(dir, CLOCK, warning -> { });

        assertEquals(recipes, Files.readString(dir.resolve("user-recipes.json")));
        assertEquals(ratings, Files.readString(dir.resolve("ratings.json")));
    }

    @Test
    void savingAndUndoUpdateTheUserRecipeFile() {
        ApplicationContext ctx = withSavedRecipe(dir, new ArrayList<>(), "Lemon Rice");
        assertTrue(Files.exists(ctx.userRecipesFile()));

        ctx.history().undo();

        assertFalse(Files.exists(ctx.userRecipesFile()), "the file is deleted when the last user recipe is gone");
        assertFalse(ctx.recipeBook().contains("AI001"));
    }

    @Test
    void ratingChangeWritesRatingsFile() throws IOException {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        assertFalse(Files.exists(ctx.ratingsFile()));

        ctx.preferences().rate("R101", 3);

        assertTrue(Files.readString(ctx.ratingsFile()).contains("\"R101\" : 3"));
    }

    @Test
    void userRecipeWithBundledIdIsRejected() {
        Recipe clash = new Recipe("R101", "My Stir-Fry", 1, 10, List.of("Cook"),
                ApplicationContext.create(dir, CLOCK).recipeBook().get("R101").orElseThrow().getIngredients());
        RecipeRepository.save(dir.resolve("user-recipes.json"), List.of(clash));

        RepositoryException e = assertThrows(RepositoryException.class, () -> ApplicationContext.create(dir, CLOCK));

        assertTrue(e.getMessage().contains("user-recipes.json") && e.getMessage().contains("R101"), e.getMessage());
    }

    @Test
    void corruptUserFilesStopStartupAndAreKept() throws IOException {
        Files.writeString(dir.resolve("ratings.json"), "{\"ratings\": ");
        assertThrows(RepositoryException.class, () -> ApplicationContext.create(dir, CLOCK));
        assertEquals("{\"ratings\": ", Files.readString(dir.resolve("ratings.json")));
        Files.delete(dir.resolve("ratings.json"));

        Files.writeString(dir.resolve("user-recipes.json"), "{\"recipes\": [}");
        assertThrows(RepositoryException.class, () -> ApplicationContext.create(dir, CLOCK));
        assertEquals("{\"recipes\": [}", Files.readString(dir.resolve("user-recipes.json")));
    }

    @Test
    void ratingsForUnknownRecipesAreDroppedWithAWarning() throws IOException {
        Files.writeString(dir.resolve("ratings.json"),
                "{\"ratings\": {\"R101\": 5, \"GHOST\": 3}, \"favorites\": [\"NOPE\", \"R103\"]}");
        List<String> warnings = new ArrayList<>();

        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warnings::add);

        assertEquals(List.of("Ignored ratings for unknown recipes: GHOST, NOPE"), warnings);
        assertEquals(Optional.of(5), ctx.preferences().getRating("R101"));
        assertTrue(ctx.preferences().isFavorite("R103"));
    }

    @Test
    void removingASavedRecipePrunesAndPersistsItsRating() throws IOException {
        ApplicationContext ctx = withSavedRecipe(dir, new ArrayList<>(), "Lemon Rice");
        ctx.preferences().rate("AI001", 5);

        ctx.history().pushAndExecute(new RemoveRecipeCommand(ctx.recipeBook(), "AI001"));

        assertEquals(Optional.empty(), ctx.preferences().getRating("AI001"));
        assertFalse(Files.readString(ctx.ratingsFile()).contains("AI001"));
        assertFalse(Files.exists(ctx.userRecipesFile()));
    }

    @Test
    void scalingASavedRecipeIsPersisted() {
        ApplicationContext ctx = withSavedRecipe(dir, new ArrayList<>(), "Lemon Rice");

        ctx.history().pushAndExecute(new ScaleRecipeCommand(ctx.recipeBook(), "AI001", 4));

        assertEquals(4, ApplicationContext.create(dir, CLOCK).recipeBook().get("AI001").orElseThrow().getServings());
    }

    @Test
    void bundledRecipesAreRecognised() {
        ApplicationContext ctx = withSavedRecipe(dir, new ArrayList<>(), "Lemon Rice");

        assertTrue(ctx.isBundledRecipe("R101"));
        assertFalse(ctx.isBundledRecipe("AI001"));
    }

    @Test
    void failedRatingsSaveIsReported() throws IOException {
        List<String> warnings = new ArrayList<>();
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warnings::add);
        Files.createDirectories(ctx.ratingsFile());
        Files.writeString(ctx.ratingsFile().resolve("blocker.txt"), "x");

        ctx.preferences().rate("R101", 2);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("ratings changes were not saved"), warnings.get(0));
    }

    @Test
    void generatedIdsSkipSavedRecipesAfterReload() {
        withSavedRecipe(dir, new ArrayList<>(), "Lemon Rice");
        ApplicationContext reloaded = ApplicationContext.create(dir, CLOCK);
        reloaded.llm().useClient(new OneRecipeClient("Lime Rice"));

        assertEquals("AI002", reloaded.agent().generateRecipe("again", null).recipe().getId());
    }

    // ---- Commit 13: rule-based planning ----

    @Test
    void plannerUsesSharedStateAndNeverTouchesTheCurrentPlan() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        MealPlan current = ctx.plan();
        ctx.profile().addAllergen("peanut");

        PlanResult result = ctx.planner().generate(PlanRequest.defaults());

        assertSame(ctx.planner(), ctx.planner());
        assertSame(current, ctx.plan());
        assertEquals(28, ctx.plan().getEmptySlots().size());
        assertEquals(14, result.plan().getEmptySlots().size());
        assertFalse(result.plan().getRecipes().stream().anyMatch(r -> r.getId().equals("R109")));
        assertTrue(result.validation().isSafe());
    }

    @Test
    void plannerSkipsRecipesTheUserRatedLow() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        ctx.preferences().rate("R101", 1);

        PlanResult result = ctx.planner().generate(PlanRequest.defaults());

        assertTrue(result.notes().contains("Excluded 1 recipe(s) you rated 1-2 stars."));
        assertFalse(result.plan().getRecipes().stream().anyMatch(r -> r.getId().equals("R101")));
    }

    // ---- Commit 14: nutrition and recommendations ----

    @Test
    void nutritionFollowsTheCurrentPlan() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        ctx.plan().setMeal(1, MealType.DINNER, recipeMeal(ctx, "R103"));
        assertEquals(1, ctx.nutrition().mealsPlanned());
        assertEquals(28, ctx.nutrition().slotsTotal());

        ctx.newPlan(2);

        assertEquals(0, ctx.nutrition().mealsPlanned());
        assertEquals(8, ctx.nutrition().slotsTotal());
    }

    @Test
    void recommenderWorksOnTheBundledRecipes() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        ctx.profile().setDiet(DietType.VEGETARIAN);

        RecommendationResult result = ctx.recommender().recommend(RecommendationRequest.defaults());

        assertSame(ctx.recommender(), ctx.recommender());
        assertEquals(5, result.items().size());
        assertTrue(result.notes().contains("Pantry is empty; ranking uses your ratings and favorites only."));
        assertFalse(result.items().stream().anyMatch(item -> List.of("R101", "R104", "R106").contains(item.recipe().getId())));
    }
}
