package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Composite pattern through the {@link MealComponent} interface only.
 *
 * <p>Fixture (chicken = 100 cal / 2.00; rice = 200 cal / 0.50):
 * <pre>
 * plan (2 days)
 *   day 1: LUNCH  = [rice bowl (rice), chicken]   300 cal, 2.50
 *          DINNER = [chicken and rice]           300 cal, 2.50
 *   day 2: DINNER = [chicken and rice]           300 cal, 2.50
 * </pre>
 */
class MealComponentCompositeTest {

    private static Ingredient chicken() {
        return new Ingredient("Chicken", 100, Unit.GRAM, 100, 20, 0, 2, 2.00);
    }

    private static Ingredient rice() {
        return new Ingredient("Rice", 100, Unit.GRAM, 200, 4, 45, 1, 0.50);
    }

    private static Recipe chickenAndRice() {
        return new Recipe("r1", "Chicken and Rice", 2, 20, List.of("Cook rice", "Grill chicken"),
                List.of(chicken(), rice()));
    }

    private static Recipe riceBowl() {
        return new Recipe("r2", "Rice Bowl", 1, 10, List.of(), List.of(rice()));
    }

    private static MealPlan twoDayPlan() {
        MealPlan plan = new MealPlan("Test week", 2);
        plan.setMeal(1, MealType.LUNCH, new Meal(MealType.LUNCH, null, List.of(riceBowl(), chicken())));
        plan.setMeal(1, MealType.DINNER, new Meal(MealType.DINNER, null, List.of(chickenAndRice())));
        plan.setMeal(2, MealType.DINNER, new Meal(MealType.DINNER, null, List.of(chickenAndRice())));
        return plan;
    }

    private static void assertClose(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) < 1e-9, "expected " + expected + " but was " + actual);
    }

    @Test
    void everyLevelAnswersTheSameCallsPolymorphically() {
        MealPlan plan = twoDayPlan();
        MealComponent leaf = chicken();
        MealComponent recipe = chickenAndRice();
        MealComponent meal = plan.getMeal(1, MealType.LUNCH).orElseThrow();
        MealComponent day = plan.getDay(1);
        MealComponent root = plan;

        assertClose(100, leaf.getCalories());
        assertClose(2.00, leaf.getCost());
        assertClose(300, recipe.getCalories());
        assertClose(2.50, recipe.getCost());
        assertClose(300, meal.getCalories());
        assertClose(2.50, meal.getCost());
        assertClose(600, day.getCalories());
        assertClose(5.00, day.getCost());
        assertClose(300, plan.getDay(2).getCalories());
        assertClose(900, root.getCalories());
        assertClose(7.50, root.getCost());
    }

    @Test
    void allFiveValuesAreSummed() {
        MealComponent root = twoDayPlan();

        // 3 chicken + 3 rice across the plan.
        assertClose(3 * 100 + 3 * 200, root.getCalories());
        assertClose(3 * 20 + 3 * 4, root.getProteinG());
        assertClose(3 * 0 + 3 * 45, root.getCarbsG());
        assertClose(3 * 2 + 3 * 1, root.getFatG());
        assertClose(3 * 2.00 + 3 * 0.50, root.getCost());
    }

    @Test
    void ingredientsAreFlattenedInTraversalOrder() {
        MealPlan plan = twoDayPlan();

        assertEquals(List.of(chicken()), chicken().getIngredients());
        assertEquals(List.of(chicken(), rice()), chickenAndRice().getIngredients());
        assertEquals(List.of(rice(), chicken()), plan.getMeal(1, MealType.LUNCH).orElseThrow().getIngredients());
        assertEquals(List.of(rice(), chicken(), chicken(), rice()), plan.getDay(1).getIngredients());

        List<Ingredient> all = plan.getIngredients();
        assertEquals(6, all.size());
        assertEquals(List.of(rice(), chicken(), chicken(), rice(), chicken(), rice()), all);
    }

    @Test
    void recipesAreCollectedAtEveryLevel() {
        MealPlan plan = twoDayPlan();

        assertEquals(List.of(), chicken().getRecipes());
        assertEquals(List.of(chickenAndRice()), chickenAndRice().getRecipes());
        assertEquals(List.of(riceBowl()), plan.getMeal(1, MealType.LUNCH).orElseThrow().getRecipes());
        assertEquals(List.of(riceBowl(), chickenAndRice()), plan.getDay(1).getRecipes());
        assertEquals(List.of(riceBowl(), chickenAndRice(), chickenAndRice()), plan.getRecipes());
    }
}
