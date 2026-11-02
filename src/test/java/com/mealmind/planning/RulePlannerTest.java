package com.mealmind.planning;

import static com.mealmind.planning.PlanningFixtures.PEANUT_NOODLES;
import static com.mealmind.planning.PlanningFixtures.allFour;
import static com.mealmind.planning.PlanningFixtures.book;
import static com.mealmind.planning.PlanningFixtures.grams;
import static com.mealmind.planning.PlanningFixtures.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.DayPlan;
import com.mealmind.model.Meal;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.persistence.RecipeRepository;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.ViolationType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RulePlannerTest {

    private static final List<MealType> LUNCH_DINNER = List.of(MealType.LUNCH, MealType.DINNER);

    private Pantry pantry;
    private UserProfile profile;
    private RecipeBook recipes;
    private RecipePreferences preferences;
    private RulePlanner planner;

    @BeforeEach
    void setUp() {
        pantry = new Pantry();
        profile = new UserProfile();
        use(allFour());
    }

    private void use(RecipeBook book) {
        recipes = book;
        preferences = new RecipePreferences(book);
        planner = new RulePlanner(book, pantry, new ConstraintValidator(profile), preferences, profile);
    }

    private PlanResult generate(int days, String strategy, List<MealType> slots, Double budget) {
        return planner.generate(new PlanRequest(days, PlanningStrategies.byId(strategy), slots, budget));
    }

    /** Recipe ids in fill order: day by day, slot by slot in the given order. */
    private static List<String> ids(PlanResult result, List<MealType> slots) {
        List<String> ids = new ArrayList<>();
        for (DayPlan day : result.plan().getDays()) {
            for (MealType type : slots) {
                day.getMeal(type).ifPresent(meal -> ids.add(meal.getRecipes().get(0).getId()));
            }
        }
        return ids;
    }

    @Test
    void pantryFirstPrefersCoveredRecipesAndUsesEachBeforeRepeating() {
        pantry.addItem(new PantryItem("Rice", 500, Unit.GRAM));

        PlanResult result = generate(3, "pantry-first", LUNCH_DINNER, null);

        assertEquals(List.of("P1", "P3", "P2", "P4", "P1", "P3"), ids(result, LUNCH_DINNER));
        assertEquals("Weekly Plan", result.plan().getName());
        assertEquals(3, result.plan().getNumberOfDays());
        assertEquals("Strategy: Pantry-First (rule-based, no AI)", result.notes().get(0));
        assertTrue(result.notes().contains("Pool has only 4 safe recipe(s); recipes repeat."));
    }

    @Test
    void budgetAndHighProteinOrderByTheirScores() {
        assertEquals(List.of("P1", "P4", "P3", "P2"), ids(generate(2, "budget", LUNCH_DINNER, null), LUNCH_DINNER));
        assertEquals(List.of("P2", "P3", "P4", "P1"), ids(generate(2, "high-protein", LUNCH_DINNER, null), LUNCH_DINNER));
    }

    @Test
    void unsafeRecipesAreNeverChosen() {
        profile.addAllergen("peanut");

        PlanResult result = generate(7, "budget", List.of(MealType.values()), null);

        assertFalse(ids(result, List.of(MealType.values())).contains(PEANUT_NOODLES.getId()));
        assertEquals(28, ids(result, List.of(MealType.values())).size());
        assertTrue(result.validation().isSafe());
        assertTrue(result.notes().contains("Pool has only 3 safe recipe(s); recipes repeat."));
    }

    @Test
    void noSafeRecipeThrowsAndNothingIsBuilt() {
        use(book(PEANUT_NOODLES));
        profile.addAllergen("peanut");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> generate(1, "budget", LUNCH_DINNER, null));
        assertEquals("No recipes in the recipe book are safe for this profile", e.getMessage());
    }

    @Test
    void lowRatedRecipesAreDroppedWhenOthersExist() {
        preferences.rate("P1", 2);
        preferences.rate("P4", 1);
        preferences.rate("P3", 3);

        PlanResult result = generate(1, "budget", LUNCH_DINNER, null);

        assertEquals(List.of("P3", "P2"), ids(result, LUNCH_DINNER));
        assertTrue(result.notes().contains("Excluded 2 recipe(s) you rated 1-2 stars."));
        assertFalse(result.notes().stream().anyMatch(n -> n.startsWith("Pool has only")));
    }

    @Test
    void allLowRatedRecipesAreUsedAnyway() {
        for (Recipe recipe : recipes.getAll()) {
            preferences.rate(recipe.getId(), 1);
        }

        PlanResult result = generate(1, "budget", LUNCH_DINNER, null);

        assertEquals(List.of("P1", "P4"), ids(result, LUNCH_DINNER));
        assertTrue(result.notes().contains("All safe recipes are rated low; using them anyway."));
        assertFalse(result.notes().stream().anyMatch(n -> n.startsWith("Excluded")));
    }

    @Test
    void equalScoresPreferFavoritesThenRatingThenId() {
        use(book(recipe("T2", "Twin B", grams("Oats", 100, 10, 2.00)), recipe("T1", "Twin A", grams("Oats", 100, 10, 2.00))));
        List<MealType> one = List.of(MealType.DINNER);

        assertEquals(List.of("T1"), ids(generate(1, "budget", one, null), one));
        preferences.rate("T2", 4);
        assertEquals(List.of("T2"), ids(generate(1, "budget", one, null), one));
        preferences.rate("T1", 5);
        assertEquals(List.of("T1"), ids(generate(1, "budget", one, null), one));
        preferences.setFavorite("T2", true);
        assertEquals(List.of("T2"), ids(generate(1, "budget", one, null), one));
    }

    @Test
    void mealsAreScaledToHouseholdServingsAndLabelledWithTheRecipeName() {
        profile.setHouseholdServings(3);

        PlanResult result = generate(1, "high-protein", List.of(MealType.DINNER, MealType.BREAKFAST), null);

        Meal dinner = result.plan().getMeal(1, MealType.DINNER).orElseThrow();
        assertEquals(MealType.DINNER, dinner.getType());
        assertEquals("Chicken Plate", dinner.getName());
        assertEquals(3, dinner.getRecipes().get(0).getServings());
        assertEquals(18.0, dinner.getCost(), 1e-9);
        assertEquals("Tofu Stir-Fry", result.plan().getMeal(1, MealType.BREAKFAST).orElseThrow().getName());
        assertTrue(result.plan().getMeal(1, MealType.LUNCH).isEmpty());
        assertEquals(6, recipes.get("P2").orElseThrow().getIngredients().get(0).getCost(), 1e-9);
    }

    @Test
    void budgetNotesUseTwoDecimals() {
        // 1 day of budget: Rice Bowl $1.00 + Peanut Noodles $3.00.
        assertTrue(generate(1, "budget", LUNCH_DINNER, 3.5).notes()
                .contains("Plan cost $4.00 exceeds your budget of $3.50"));
        assertTrue(generate(1, "budget", LUNCH_DINNER, 4.0).notes()
                .contains("Plan cost $4.00 is within your budget of $4.00"));
        assertFalse(generate(1, "budget", LUNCH_DINNER, null).notes().stream().anyMatch(n -> n.startsWith("Plan cost")));
    }

    @Test
    void validationReportsAdvisoriesForThePlan() {
        profile.setCalorieTarget(800);
        profile.setHouseholdServings(5);

        PlanResult result = generate(1, "budget", LUNCH_DINNER, null);

        assertTrue(result.validation().isSafe());
        assertEquals(ViolationType.CALORIES, result.validation().getAdvisories().get(0).type());
    }

    @Test
    void isDeterministicAndDoesNotChangeTheInputs() {
        pantry.addItem(new PantryItem("Rice", 500, Unit.GRAM));

        PlanResult first = generate(7, "pantry-first", LUNCH_DINNER, 100.0);
        PlanResult second = generate(7, "pantry-first", LUNCH_DINNER, 100.0);

        assertEquals(ids(first, LUNCH_DINNER), ids(second, LUNCH_DINNER));
        assertEquals(first.notes(), second.notes());
        assertEquals(500, pantry.availableQuantity("Rice", Unit.GRAM), 1e-9);
        assertEquals(4, recipes.size());
    }

    @Test
    void constructorRejectsNulls() {
        ConstraintValidator validator = new ConstraintValidator(profile);
        assertThrows(NullPointerException.class, () -> new RulePlanner(null, pantry, validator, preferences, profile));
        assertThrows(NullPointerException.class, () -> new RulePlanner(recipes, pantry, validator, preferences, null));
        assertThrows(NullPointerException.class, () -> planner.generate(null));
    }

    @Test
    void fillsEveryDayAndSlot() {
        PlanResult result = generate(7, "budget", List.of(MealType.values()), null);

        assertTrue(result.plan().getEmptySlots().isEmpty());
        assertEquals(28, ids(result, List.of(MealType.values())).size());
    }

    @Test
    void vegetarianProfileNeverGetsMeatAndEveryMealPassesTheValidator() {
        profile.setDiet(DietType.VEGETARIAN);
        ConstraintValidator validator = new ConstraintValidator(profile);

        PlanResult result = generate(7, "high-protein", List.of(MealType.values()), null);

        assertFalse(ids(result, List.of(MealType.values())).contains("P2"));
        result.plan().getRecipes().forEach(recipe -> assertTrue(validator.isSafe(recipe), recipe.getName()));
        assertTrue(result.validation().isSafe());
    }

    @Test
    void bundledSamplesWithAVegetarianProfileGiveASafePlanWithoutMeatOrFish() {
        use(book(RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES).toArray(Recipe[]::new)));
        profile.setDiet(DietType.VEGETARIAN);

        PlanResult result = planner.generate(PlanRequest.defaults());

        List<String> ids = ids(result, LUNCH_DINNER);
        assertEquals(14, ids.size());
        for (String meatOrFish : List.of("R101", "R104", "R106")) {
            assertFalse(ids.contains(meatOrFish), meatOrFish);
        }
        assertTrue(result.validation().isSafe());
    }

    @Test
    void fourSafeRecipesForFourSlotsAreEachUsedOnce() {
        PlanResult result = generate(1, "high-protein", List.of(MealType.values()), null);

        assertEquals(4, ids(result, List.of(MealType.values())).stream().distinct().count());
        assertFalse(result.notes().stream().anyMatch(n -> n.startsWith("Pool has only")));
    }

    @Test
    void scalingChangesQuantitiesButNotPerServingValues() {
        profile.setHouseholdServings(3);

        Recipe placed = generate(1, "high-protein", List.of(MealType.DINNER), null).plan()
                .getMeal(1, MealType.DINNER).orElseThrow().getRecipes().get(0);

        assertEquals(600, placed.getIngredients().get(0).getQuantity(), 1e-9);
        assertEquals(40, placed.proteinPerServing(), 1e-9);
        assertEquals(6, placed.costPerServing(), 1e-9);
    }

    @Test
    void eachResultIsANewDetachedPlan() {
        PlanResult first = generate(1, "budget", LUNCH_DINNER, null);
        PlanResult second = generate(1, "budget", LUNCH_DINNER, null);

        first.plan().clearMeal(1, MealType.LUNCH);

        assertTrue(second.plan().getMeal(1, MealType.LUNCH).isPresent());
    }
}
