package com.mealmind.tools;

import static com.mealmind.tools.ToolTestSupport.error;
import static com.mealmind.tools.ToolTestSupport.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.persistence.RecipeRepository;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeSearchToolTest {

    private RecipeBook book;
    private Pantry pantry;
    private UserProfile profile;
    private RecipeSearchTool tool;

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES).forEach(book::put);
        pantry = new Pantry();
        profile = new UserProfile();
        tool = new RecipeSearchTool(book, pantry, new ConstraintValidator(profile));
    }

    private static List<String> ids(JsonNode result) {
        return result.get("recipes").findValuesAsText("id");
    }

    /** A tool over custom recipes only. */
    private RecipeSearchTool customTool(Recipe... recipes) {
        RecipeBook custom = new RecipeBook();
        for (Recipe recipe : recipes) {
            custom.put(recipe);
        }
        return new RecipeSearchTool(custom, pantry, new ConstraintValidator(profile));
    }

    private static Recipe recipe(String id, Ingredient... ingredients) {
        return new Recipe(id, "Recipe " + id, 1, 10, List.of("Cook"), List.of(ingredients));
    }

    private static Ingredient grams(String name, double quantity) {
        return new Ingredient(name, quantity, Unit.GRAM, 100, 5, 10, 2, 1.00);
    }

    // ---- search filters ----

    @Test
    void queryMatchesNameAndIngredientsIgnoringCase() {
        assertEquals(List.of("R103"), ids(ok(tool, "{\"query\": \"LENTIL\"}")));
        assertEquals(List.of("R101", "R103", "R104"), ids(ok(tool, "{\"query\": \"garlic\"}")));
    }

    @Test
    void prepAndCalorieFilters() {
        assertEquals(List.of("R102", "R109", "R110"), ids(ok(tool, "{\"maxPrepMinutes\": 10}")));
        assertEquals(List.of("R103", "R105", "R110"), ids(ok(tool, "{\"maxCaloriesPerServing\": 400}")));
        assertEquals("maxPrepMinutes must be 0 or more", error(tool, "{\"maxPrepMinutes\": -1}"));
        assertEquals("maxCaloriesPerServing must be 1 or more", error(tool, "{\"maxCaloriesPerServing\": 0}"));
    }

    @Test
    void limitDefaultsToFiveAndIsBounded() {
        JsonNode result = ok(tool, "{}");

        assertEquals(10, result.get("totalMatches").asInt());
        assertEquals(5, result.get("returned").asInt());
        assertEquals(List.of("R101", "R102", "R103", "R104", "R105"), ids(result));
        assertEquals(2, ok(tool, "{\"limit\": 2}").get("recipes").size());
        assertEquals("limit must be between 1 and 10, got 0", error(tool, "{\"limit\": 0}"));
        assertEquals("limit must be between 1 and 10, got 11", error(tool, "{\"limit\": 11}"));
    }

    @Test
    void resultShapeIncludesPerServingAndCoverage() {
        JsonNode r103 = ok(tool, "{\"query\": \"lentil\"}").get("recipes").get(0);

        assertEquals("Red Lentil Soup", r103.get("name").asText());
        assertEquals(4, r103.get("servings").asInt());
        assertEquals(40, r103.get("prepMinutes").asInt());
        assertEquals(331.0, r103.get("perServing").get("calories").asDouble());
        assertEquals(1.14, r103.get("perServing").get("cost").asDouble());
        assertEquals(0, r103.get("pantryCoveragePercent").asInt());
        assertEquals(8, r103.get("missingIngredients").size());
    }

    // ---- pantry coverage (F03, deterministic part) ----

    @Test
    void coverageMath() {
        pantry.addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));    // more than needed: covered
        pantry.addItem(new PantryItem("Beans", 50, Unit.GRAM));      // less than needed: missing
        pantry.addItem(new PantryItem("Flour", 1, Unit.LITER));      // incompatible unit: missing
        RecipeSearchTool custom = customTool(
                recipe("c1", grams("Rice", 200), grams("Beans", 100), grams("Flour", 100)),
                recipe("c2", grams("Rice", 300)),
                recipe("c3", grams("Tofu", 100)));

        JsonNode result = ok(custom, "{}");

        JsonNode c1 = result.get("recipes").get(0);
        assertEquals(33, c1.get("pantryCoveragePercent").asInt());
        assertEquals(List.of("Beans", "Flour"), List.of(c1.get("missingIngredients").get(0).asText(),
                c1.get("missingIngredients").get(1).asText()));
        assertEquals(100, result.get("recipes").get(1).get("pantryCoveragePercent").asInt());
        assertEquals(0, result.get("recipes").get(2).get("pantryCoveragePercent").asInt());
    }

    @Test
    void pantryFirstSortsByCoverageThenFewerMissingThenId() {
        pantry.addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));
        RecipeSearchTool custom = customTool(
                recipe("a1", grams("Tofu", 100), grams("Kale", 100)),            // 0%, 2 missing
                recipe("b1", grams("Rice", 100), grams("Tofu", 100)),            // 50%, 1 missing
                recipe("c1", grams("Rice", 100)),                                // 100%
                recipe("d1", grams("Tofu", 100)));                               // 0%, 1 missing

        assertEquals(List.of("a1", "b1", "c1", "d1"), ids(ok(custom, "{}")));
        assertEquals(List.of("c1", "b1", "d1", "a1"), ids(ok(custom, "{\"pantryFirst\": true}")));
    }

    // ---- safety ----

    @Test
    void unsafeRecipesAreHiddenAndOnlyCounted() {
        profile.setDiet(DietType.VEGETARIAN);
        profile.addAllergen("peanut");

        JsonNode result = ok(tool, "{\"limit\": 10}");

        // Hidden: R101 chicken, R104 ground beef, R106 salmon, R109 peanut butter.
        assertEquals(4, result.get("excludedForSafety").asInt());
        assertEquals(6, result.get("totalMatches").asInt());
        assertEquals(List.of("R102", "R103", "R105", "R107", "R108", "R110"), ids(result));
        assertFalse(result.toString().contains("Chicken"), "no detail about hidden recipes");
    }

    @Test
    void profileChangesTakeEffectOnNextCall() {
        assertTrue(ids(ok(tool, "{\"query\": \"stir\"}")).contains("R101"));

        profile.setDiet(DietType.VEGETARIAN);

        JsonNode after = ok(tool, "{\"query\": \"stir\"}");
        assertEquals(List.of(), ids(after));
        assertEquals(1, after.get("excludedForSafety").asInt());
    }

    // ---- id lookup ----

    @Test
    void idReturnsFullDetails() {
        JsonNode r103 = ok(tool, "{\"id\": \"R103\"}");

        assertEquals("R103", r103.get("id").asText());
        assertEquals(5, r103.get("instructions").size());
        assertEquals("Red lentils", r103.get("ingredients").get(0).get("name").asText());
        assertEquals(250.0, r103.get("ingredients").get(0).get("quantity").asDouble());
        assertEquals("g", r103.get("ingredients").get(0).get("unit").asText());
        assertTrue(r103.has("perServing") && r103.has("pantryCoveragePercent") && r103.has("missingIngredients"));
    }

    @Test
    void idErrors() {
        assertEquals("No recipe with id 'R999'", error(tool, "{\"id\": \"R999\"}"));
        assertEquals("'id' cannot be combined with search filters", error(tool, "{\"id\": \"R101\", \"limit\": 2}"));

        profile.setDiet(DietType.VEGETARIAN);
        String unsafe = error(tool, "{\"id\": \"R101\"}");
        assertEquals("Recipe R101 is not available for this user's profile", unsafe);
        assertFalse(unsafe.contains("chicken") || unsafe.contains("vegetarian"));
    }

    @Test
    void doesNotModifyTheRecipeBook() {
        List<Recipe> before = book.getAll();

        ok(tool, "{\"pantryFirst\": true, \"limit\": 10}");
        ok(tool, "{\"id\": \"R101\"}");

        assertEquals(before, book.getAll());
        assertEquals(2, book.get("R101").orElseThrow().getServings());
    }
}
