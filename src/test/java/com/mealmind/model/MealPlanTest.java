package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MealPlanTest {

    private static Recipe recipe(String id, double calories, double cost) {
        Ingredient food = new Ingredient("Food " + id, 100, Unit.GRAM, calories, 0, 0, 0, cost);
        return new Recipe(id, "Recipe " + id, 1, 10, List.of(), List.of(food));
    }

    private static Meal meal(MealType type, Recipe recipe) {
        return new Meal(type, null, List.of(recipe));
    }

    @Test
    void rejectsDayCountOutOfRangeAndBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new MealPlan("Week", 0));
        assertThrows(IllegalArgumentException.class, () -> new MealPlan("Week", 8));
        assertThrows(IllegalArgumentException.class, () -> new MealPlan(" ", 3));
    }

    @Test
    void createsDaysNumberedOneToN() {
        MealPlan plan = new MealPlan("Week", 5);

        assertEquals("Week", plan.getName());
        assertEquals(5, plan.getNumberOfDays());
        assertEquals(5, plan.getDays().size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, plan.getDays().get(i).getDayNumber());
        }
        assertThrows(UnsupportedOperationException.class, () -> plan.getDays().add(new DayPlan(6)));
    }

    @Test
    void getDayRejectsOutOfRange() {
        MealPlan plan = new MealPlan("Week", 3);

        assertThrows(IllegalArgumentException.class, () -> plan.getDay(0));
        assertThrows(IllegalArgumentException.class, () -> plan.getDay(4));
    }

    @Test
    void slotMethodsDelegateToTheDay() {
        MealPlan plan = new MealPlan("Week", 2);
        Meal dinner = meal(MealType.DINNER, recipe("r1", 500, 4.00));

        assertEquals(Optional.empty(), plan.setMeal(2, MealType.DINNER, dinner));
        assertEquals(Optional.of(dinner), plan.getMeal(2, MealType.DINNER));
        assertEquals(Optional.of(dinner), plan.getDay(2).getMeal(MealType.DINNER));
        assertEquals(Optional.empty(), plan.getMeal(1, MealType.DINNER));

        assertEquals(Optional.of(dinner), plan.clearMeal(2, MealType.DINNER));
        assertEquals(Optional.empty(), plan.getDay(2).getMeal(MealType.DINNER));
    }

    @Test
    void emptySlotsAreOrderedAndShrinkAsSlotsFill() {
        MealPlan plan = new MealPlan("Week", 2);

        List<MealPlan.Slot> expected = List.of(
                new MealPlan.Slot(1, MealType.BREAKFAST), new MealPlan.Slot(1, MealType.LUNCH),
                new MealPlan.Slot(1, MealType.DINNER), new MealPlan.Slot(1, MealType.SNACK),
                new MealPlan.Slot(2, MealType.BREAKFAST), new MealPlan.Slot(2, MealType.LUNCH),
                new MealPlan.Slot(2, MealType.DINNER), new MealPlan.Slot(2, MealType.SNACK));
        assertEquals(expected, plan.getEmptySlots());

        plan.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH, recipe("r1", 300, 2.00)));
        plan.setMeal(2, MealType.SNACK, meal(MealType.SNACK, recipe("r2", 100, 1.00)));

        List<MealPlan.Slot> remaining = plan.getEmptySlots();
        assertEquals(6, remaining.size());
        assertEquals(new MealPlan.Slot(1, MealType.BREAKFAST), remaining.get(0));
        assertEquals(new MealPlan.Slot(1, MealType.DINNER), remaining.get(1));
        assertEquals(new MealPlan.Slot(2, MealType.DINNER), remaining.get(5));
    }

    @Test
    void traceabilityAliasesMatchCompositeMethods() {
        MealPlan plan = new MealPlan("Week", 2);
        plan.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH, recipe("r1", 300, 2.00)));
        plan.setMeal(2, MealType.LUNCH, meal(MealType.LUNCH, recipe("r1", 300, 2.00)));

        assertEquals(plan.getIngredients(), plan.getRequiredIngredients());
        assertEquals(2, plan.getRequiredIngredients().size());
        assertEquals(plan.getRecipes(), plan.getConstituentRecipes());
        assertEquals(2, plan.getConstituentRecipes().size());
    }

    @Test
    void totalsChangeWhenAMealIsReplaced() {
        MealPlan plan = new MealPlan("Week", 2);
        plan.setMeal(1, MealType.DINNER, meal(MealType.DINNER, recipe("r1", 800, 6.00)));
        plan.setMeal(2, MealType.DINNER, meal(MealType.DINNER, recipe("r2", 500, 3.00)));
        assertEquals(1300, plan.getCalories(), 1e-9);
        assertEquals(9.00, plan.getCost(), 1e-9);

        plan.setMeal(1, MealType.DINNER, meal(MealType.DINNER, recipe("r3", 400, 2.50)));
        assertEquals(900, plan.getCalories(), 1e-9);
        assertEquals(5.50, plan.getCost(), 1e-9);
    }
}
