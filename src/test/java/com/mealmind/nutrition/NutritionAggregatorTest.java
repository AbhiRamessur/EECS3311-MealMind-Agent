package com.mealmind.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.DayPlan;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.profile.UserProfile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NutritionAggregatorTest {

    private static final double EPS = 1e-9;

    /** 400 kcal, P 20, C 60, F 10, $2.00. */
    static final Recipe OATS = recipe("N1", "Oat Bowl", ingredient("Oats", 400, 20, 60, 10, 2.00));
    /** 800 kcal, P 55, C 45, F 21, $6.00. */
    static final Recipe CHICKEN_RICE = recipe("N2", "Chicken Rice", ingredient("Chicken", 600, 50, 0, 20, 5.00),
            ingredient("Rice", 200, 5, 45, 1, 1.00));

    private UserProfile profile;
    private MealPlan plan;

    static Ingredient ingredient(String name, double kcal, double protein, double carbs, double fat, double cost) {
        return new Ingredient(name, 100, Unit.GRAM, kcal, protein, carbs, fat, cost);
    }

    static Recipe recipe(String id, String name, Ingredient... ingredients) {
        return new Recipe(id, name, 1, 10, List.of("Cook"), List.of(ingredients));
    }

    private static Meal meal(MealType type, Recipe recipe) {
        return new Meal(type, recipe.getName(), List.of(recipe));
    }

    @BeforeEach
    void setUp() {
        profile = new UserProfile();
        plan = new MealPlan("Weekly Plan", 3);
    }

    /** Day 1: lunch oats + dinner chicken rice; day 2 empty; day 3: breakfast oats. */
    private void fillPlan() {
        plan.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH, OATS));
        plan.setMeal(1, MealType.DINNER, meal(MealType.DINNER, CHICKEN_RICE));
        plan.setMeal(3, MealType.BREAKFAST, meal(MealType.BREAKFAST, OATS));
    }

    @Test
    void emptyPlanHasNoDaysAndZeroFigures() {
        NutritionSummary summary = NutritionAggregator.aggregate(plan, profile);

        assertEquals(List.of(), summary.days());
        assertEquals(Macros.ZERO, summary.totals());
        assertEquals(Macros.ZERO, summary.dailyAverage());
        assertEquals(0, summary.mealsPlanned());
        assertEquals(12, summary.slotsTotal());
        assertEquals(List.of(), summary.unknownIngredients());
    }

    @Test
    void perDayTotalAndAverageFiguresAreExact() {
        fillPlan();

        NutritionSummary summary = NutritionAggregator.aggregate(plan, profile);

        assertEquals(List.of(new DayNutrition(1, new Macros(1200, 75, 105, 31, 8.00), 2),
                new DayNutrition(3, new Macros(400, 20, 60, 10, 2.00), 1)), summary.days());
        assertEquals(new Macros(1600, 95, 165, 41, 10.00), summary.totals());
        // Divided by the 2 days that have meals, not by 3.
        assertEquals(new Macros(800, 47.5, 82.5, 20.5, 5.00), summary.dailyAverage());
        assertEquals(3, summary.mealsPlanned());
        assertEquals(12, summary.slotsTotal());
    }

    @Test
    void progressOnlyForTargetsThatAreSet() {
        fillPlan();
        assertEquals(List.of(), NutritionAggregator.aggregate(plan, profile).progress());

        profile.setCalorieTarget(2000);
        profile.setProteinTargetG(100.0);
        profile.setFatTargetG(40.0);

        List<TargetProgress> progress = NutritionAggregator.aggregate(plan, profile).progress();
        assertEquals(List.of(new TargetProgress("Calories", 800, 2000, 40), new TargetProgress("Protein", 47.5, 100, 48),
                new TargetProgress("Fat", 20.5, 40, 51)), progress);
    }

    @Test
    void unknownIngredientsAreListedOnceSortedIgnoringCase() {
        Recipe odd = recipe("N3", "Odd Dish", ingredient("mystery SAUCE", 0, 0, 0, 0, 0.5), ingredient("Water", 0, 0, 0, 0, 0),
                ingredient("apple glaze", 0, 0, 0, 0, 0.5), ingredient("Bread", 250, 8, 48, 3, 1.0));
        Recipe odder = recipe("N4", "Odder Dish", ingredient("Mystery sauce", 0, 0, 0, 0, 0.5), ingredient("Bread", 250, 8, 48, 3, 1.0));
        plan.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH, odd));
        plan.setMeal(2, MealType.LUNCH, meal(MealType.LUNCH, odder));

        assertEquals(List.of("apple glaze", "mystery SAUCE"), NutritionAggregator.aggregate(plan, profile).unknownIngredients());
    }

    @Test
    void dayTotalsEqualTheSumOfTheirMealsComputedIndependently() {
        fillPlan();
        NutritionSummary summary = NutritionAggregator.aggregate(plan, profile);

        for (DayNutrition day : summary.days()) {
            DayPlan dayPlan = plan.getDay(day.day());
            double kcal = 0;
            double protein = 0;
            double cost = 0;
            for (MealType type : dayPlan.getFilledSlots()) {
                for (Ingredient ingredient : dayPlan.getMeal(type).orElseThrow().getIngredients()) {
                    kcal += ingredient.getCalories();
                    protein += ingredient.getProteinG();
                    cost += ingredient.getCost();
                }
            }
            assertEquals(kcal, day.macros().calories(), EPS);
            assertEquals(protein, day.macros().proteinG(), EPS);
            assertEquals(cost, day.macros().cost(), EPS);
        }
    }

    @Test
    void aliasAndNullChecks() {
        fillPlan();

        assertEquals(NutritionAggregator.aggregate(plan, profile), NutritionAggregator.aggregateMacros(plan, profile));
        assertThrows(NullPointerException.class, () -> NutritionAggregator.aggregate(null, profile));
        assertThrows(NullPointerException.class, () -> NutritionAggregator.aggregate(plan, null));
        assertThrows(IllegalArgumentException.class, () -> Macros.ZERO.dividedBy(0));
        assertThrows(UnsupportedOperationException.class, () -> NutritionAggregator.aggregate(plan, profile).days().clear());
    }
}
