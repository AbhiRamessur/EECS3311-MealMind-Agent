package com.mealmind.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealComponent;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConstraintValidatorTest {

    private UserProfile profile;
    private ConstraintValidator validator;

    @BeforeEach
    void setUp() {
        profile = new UserProfile();
        validator = new ConstraintValidator(profile);
    }

    // ---- helpers ----

    private static Ingredient food(String name) {
        return new Ingredient(name, 100, Unit.GRAM, 100, 10, 10, 5, 1.00);
    }

    private static Ingredient food(String name, double calories, double protein, double carbs, double fat, double cost) {
        return new Ingredient(name, 100, Unit.GRAM, calories, protein, carbs, fat, cost);
    }

    private static Recipe recipe(String id, Ingredient... ingredients) {
        return new Recipe(id, "Recipe " + id, 1, 10, List.of(), List.of(ingredients));
    }

    private static Meal meal(MealType type, MealComponent... parts) {
        return new Meal(type, null, List.of(parts));
    }

    private boolean flagged(String ingredientName) {
        return !validator.isSafe(food(ingredientName));
    }

    private List<String> messages(ValidationResult result) {
        return result.getViolations().stream().map(Violation::message).toList();
    }

    // ---- allergens ----

    @Test
    void peanutAllergyFlagsPeanutProducts() {
        profile.addAllergen("peanut");

        assertTrue(flagged("Peanut Butter"));
        assertTrue(flagged("Roasted Peanuts"));
        assertTrue(flagged("groundnut oil"));
        // Decision: hyphens split words, so "Peanut-free granola" contains the word "peanut" and IS flagged.
        // The validator prefers false positives to false negatives.
        assertTrue(flagged("Peanut-free granola"));
        assertFalse(flagged("Rolled oats"));
    }

    @Test
    void nutGroupFlagsTreeNutsButNotNutmeg() {
        profile.addAllergen("nuts");

        assertTrue(flagged("Toasted almonds"));
        assertTrue(flagged("Walnut halves"));
        assertTrue(flagged("Cashews"));
        assertTrue(flagged("Almond Milk"));
        assertFalse(flagged("Ground nutmeg"));
    }

    @Test
    void dairyGroupHonoursExceptions() {
        profile.addAllergen("dairy");

        assertTrue(flagged("Cheddar Cheese"));
        assertTrue(flagged("Butter"));
        assertTrue(flagged("Peanut butter and cream cheese"));
        assertFalse(flagged("Peanut Butter"));
        assertFalse(flagged("Coconut Milk"));
        assertFalse(flagged("Cocoa Butter"));
    }

    @Test
    void exceptionsApplyOnlyToTheirGroup() {
        profile.addAllergen("peanut");
        assertTrue(flagged("Peanut Butter"));

        profile.removeAllergen("peanut");
        profile.addAllergen("tree nuts");
        assertTrue(flagged("Almond Milk"));
    }

    @Test
    void glutenGroupHonoursFlourExceptionsAndWholeWords() {
        profile.addAllergen("gluten");

        assertTrue(flagged("Wheat Flour"));
        assertTrue(flagged("Spaghetti pasta"));
        assertFalse(flagged("Rice Flour"));
        assertFalse(flagged("Puff pastry")); // "pasta" must not match inside "pastry"
    }

    @Test
    void pluralsUnknownAllergensCaseAndSpacing() {
        profile.addAllergen("tomato");
        profile.addAllergen("  KIWI ");

        assertTrue(flagged("Cherry tomatoes"));
        assertTrue(flagged("golden   KIWIS"));
        assertFalse(flagged("Kiwiberry jam"));
    }

    @Test
    void allergenMessagesNameTheCause() {
        profile.addAllergen("peanut");
        profile.addAllergen("nuts");

        assertEquals(List.of("Peanut Butter contains peanut, a listed allergen"),
                messages(validator.validateAgainstBlacklists(food("Peanut Butter"))));
        assertEquals(List.of("Cashews contains cashew (listed allergen: nuts)"),
                messages(validator.validateAgainstBlacklists(food("Cashews"))));
    }

    // ---- diets ----

    @Test
    void vegetarianExcludesMeatAndFish() {
        profile.setDiet(DietType.VEGETARIAN);

        assertTrue(flagged("Chicken Breast"));
        assertTrue(flagged("Beef Stock"));
        assertTrue(flagged("Salmon fillet"));
        assertFalse(flagged("Eggs"));
        assertFalse(flagged("Milk"));
        assertFalse(flagged("Vegetable Broth"));
        assertTrue(messages(validator.validateAgainstBlacklists(food("Chicken Breast"))).get(0)
                .startsWith("Chicken Breast is not allowed for a vegetarian diet"));
    }

    @Test
    void pescatarianAllowsSeafoodButNotMeat() {
        profile.setDiet(DietType.PESCATARIAN);

        assertFalse(flagged("Salmon"));
        assertFalse(flagged("Shrimp"));
        assertTrue(flagged("Bacon"));
    }

    @Test
    void veganExcludesAnimalProductsWithDairyExceptions() {
        profile.setDiet(DietType.VEGAN);

        assertTrue(flagged("Milk"));
        assertTrue(flagged("Cheddar Cheese"));
        assertTrue(flagged("Eggs"));
        assertTrue(flagged("Honey"));
        assertTrue(flagged("Chicken"));
        assertFalse(flagged("Coconut Milk"));
        assertFalse(flagged("Soy Milk"));
        assertFalse(flagged("Tofu"));
    }

    @Test
    void omnivoreFlagsNothing() {
        assertFalse(flagged("Chicken"));
        assertFalse(flagged("Honey"));
    }

    // ---- result shape ----

    @Test
    void oneIngredientCanBreakDietAndAllergenRules() {
        profile.setDiet(DietType.VEGAN);
        profile.addAllergen("dairy");

        ValidationResult result = validator.validateAgainstBlacklists(food("Whole Milk"));

        assertEquals(2, result.getViolations().size());
        assertEquals(List.of(ViolationType.ALLERGEN, ViolationType.DIET),
                result.getViolations().stream().map(Violation::type).toList());
        assertTrue(result.getViolations().stream().allMatch(v -> v.severity() == Severity.SAFETY));
    }

    @Test
    void everyCompositeLevelIsCheckableAndAllOffendersListed() {
        profile.setDiet(DietType.VEGETARIAN);
        Recipe stirFry = recipe("r1", food("Chicken"), food("Broccoli"), food("Fish sauce"));
        Meal dinner = meal(MealType.DINNER, stirFry);
        MealPlan plan = new MealPlan("Week", 2);
        plan.setMeal(2, MealType.DINNER, dinner);

        for (MealComponent component : List.<MealComponent>of(stirFry, dinner, plan.getDay(2), plan)) {
            ValidationResult result = validator.validateAgainstBlacklists(component);
            assertEquals(List.of("Chicken", "Fish sauce"), result.getViolations().stream().map(Violation::subject).toList());
        }
    }

    @Test
    void profileIsReadLive() {
        Ingredient sesame = food("Sesame seeds");
        assertTrue(validator.isSafe(sesame));

        profile.addAllergen("sesame");

        assertFalse(validator.isSafe(sesame));
    }

    // ---- targets and budget ----

    private MealPlan planWithDay1(MealComponent... meals) {
        MealPlan plan = new MealPlan("Week", 3);
        MealType[] slots = MealType.values();
        for (int i = 0; i < meals.length; i++) {
            plan.setMeal(1, slots[i], meal(slots[i], meals[i]));
        }
        return plan;
    }

    private List<ViolationType> advisoryTypes(MealPlan plan) {
        return validator.validateMacrosAndAllergens(plan).getAdvisories().stream().map(Violation::type).toList();
    }

    @Test
    void noTargetsMeansCleanResultForSafePlan() {
        assertTrue(validator.validateMacrosAndAllergens(planWithDay1(food("Rice", 5000, 0, 900, 0, 50))).isClean());
    }

    @Test
    void calorieAdvisoryAboveTenPercent() {
        profile.setCalorieTarget(2000);

        assertEquals(List.of(), advisoryTypes(planWithDay1(food("Rice", 2200, 0, 0, 0, 1))));
        MealPlan over = planWithDay1(food("Rice", 2200.5, 0, 0, 0, 1));
        assertEquals(List.of(ViolationType.CALORIES), advisoryTypes(over));
        String message = validator.validateMacrosAndAllergens(over).getAdvisories().get(0).message();
        assertEquals("Day 1: 2200.5 kcal is above 110% of your 2000 kcal target", message);
    }

    @Test
    void proteinAdvisoryNeedsThreeFilledSlots() {
        profile.setProteinTargetG(100.0);
        Ingredient light = food("Lettuce", 50, 10, 0, 0, 1);

        assertEquals(List.of(ViolationType.PROTEIN), advisoryTypes(planWithDay1(light, light, light)));
        assertEquals(List.of(), advisoryTypes(planWithDay1(light, light)));
        Ingredient enough = food("Tofu", 50, 27, 0, 0, 1);
        assertEquals(List.of(), advisoryTypes(planWithDay1(enough, enough, food("Beans", 50, 26, 0, 0, 1))));
    }

    @Test
    void carbsAndFatAdvisoriesAboveTwentyFivePercent() {
        profile.setCarbsTargetG(200.0);
        profile.setFatTargetG(40.0);

        assertEquals(List.of(), advisoryTypes(planWithDay1(food("Bread", 100, 0, 250, 50, 1))));
        assertEquals(List.of(ViolationType.CARBS, ViolationType.FAT),
                advisoryTypes(planWithDay1(food("Bread", 100, 0, 251, 51, 1))));
    }

    @Test
    void emptyDaysAreSkipped() {
        profile.setProteinTargetG(100.0);
        profile.setCalorieTarget(800);
        Ingredient light = food("Lettuce", 50, 1, 0, 0, 1);

        ValidationResult result = validator.validateMacrosAndAllergens(planWithDay1(light, light, light));

        assertEquals(List.of("Day 1"), result.getAdvisories().stream().map(Violation::subject).toList());
    }

    @Test
    void budgetIsProratedToPlanLength() {
        profile.setWeeklyBudget(70.0); // 3-day plan: allowed 30.00

        assertEquals(List.of(), advisoryTypes(planWithDay1(food("Steak", 100, 0, 0, 0, 30.00))));
        assertEquals(List.of(ViolationType.BUDGET), advisoryTypes(planWithDay1(food("Steak", 100, 0, 0, 0, 30.01))));
        MealPlan over = planWithDay1(food("Steak", 100, 0, 0, 0, 31.50));
        Violation budget = validator.validateMacrosAndAllergens(over).getAdvisories().get(0);
        assertEquals("Plan", budget.subject());
        assertEquals("Plan cost $31.50 is above your budget of $30.00 (weekly budget prorated to 3 days)",
                budget.message());
    }

    @Test
    void budgetMessageShowsCentsWhenOneCentOver() {
        profile.setWeeklyBudget(70.0); // 3-day plan: allowed 30.00

        Violation budget = validator.validateMacrosAndAllergens(planWithDay1(food("Steak", 100, 0, 0, 0, 30.01)))
                .getAdvisories().get(0);

        assertEquals("Plan cost $30.01 is above your budget of $30.00 (weekly budget prorated to 3 days)",
                budget.message());
    }

    @Test
    void advisoriesNeverMakePlanUnsafeButSafetyViolationsDo() {
        profile.setCalorieTarget(800);
        MealPlan plan = planWithDay1(food("Rice", 3000, 0, 0, 0, 1));
        assertTrue(validator.validateMacrosAndAllergens(plan).isSafe());

        profile.addAllergen("rice");
        ValidationResult result = validator.validateMacrosAndAllergens(plan);
        assertFalse(result.isSafe());
        assertEquals("1 safety issue, 1 advisory", result.summary());
    }
}
