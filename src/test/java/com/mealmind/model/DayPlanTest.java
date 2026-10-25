package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DayPlanTest {

    private static Meal meal(MealType type, double calories, double cost) {
        Ingredient food = new Ingredient("Food", 100, Unit.GRAM, calories, 0, 0, 0, cost);
        return new Meal(type, null, List.of(food));
    }

    @Test
    void rejectsDayNumbersOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new DayPlan(0));
        assertThrows(IllegalArgumentException.class, () -> new DayPlan(8));
    }

    @Test
    void emptyDayHasZeroTotalsAndAllSlotsEmpty() {
        DayPlan day = new DayPlan(3);

        assertEquals("Day 3", day.getName());
        assertEquals(3, day.getDayNumber());
        assertEquals(0, day.getCalories());
        assertEquals(0, day.getProteinG());
        assertEquals(0, day.getCarbsG());
        assertEquals(0, day.getFatG());
        assertEquals(0, day.getCost());
        assertEquals(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK),
                day.getEmptySlots());
        assertEquals(Set.of(), day.getFilledSlots());
    }

    @Test
    void setMealReturnsPreviousMeal() {
        DayPlan day = new DayPlan(1);
        Meal first = meal(MealType.DINNER, 300, 3.00);
        Meal second = meal(MealType.DINNER, 500, 4.00);

        assertEquals(Optional.empty(), day.setMeal(MealType.DINNER, first));
        assertEquals(Optional.of(first), day.setMeal(MealType.DINNER, second));
        assertEquals(Optional.of(second), day.getMeal(MealType.DINNER));
    }

    @Test
    void clearMealReturnsRemovedMealThenEmpty() {
        DayPlan day = new DayPlan(1);
        Meal dinner = meal(MealType.DINNER, 300, 3.00);
        day.setMeal(MealType.DINNER, dinner);

        assertEquals(Optional.of(dinner), day.clearMeal(MealType.DINNER));
        assertEquals(Optional.empty(), day.clearMeal(MealType.DINNER));
        assertEquals(Optional.empty(), day.getMeal(MealType.DINNER));
    }

    @Test
    void slotsAreReportedInEnumOrder() {
        DayPlan day = new DayPlan(1);
        day.setMeal(MealType.SNACK, meal(MealType.SNACK, 100, 1.00));
        day.setMeal(MealType.BREAKFAST, meal(MealType.BREAKFAST, 200, 1.00));

        assertEquals(List.of(MealType.BREAKFAST, MealType.SNACK), List.copyOf(day.getFilledSlots()));
        assertEquals(List.of(MealType.LUNCH, MealType.DINNER), day.getEmptySlots());
        assertThrows(UnsupportedOperationException.class, () -> day.getFilledSlots().add(MealType.LUNCH));
    }

    @Test
    void totalsUpdateAfterSetAndClear() {
        DayPlan day = new DayPlan(1);
        day.setMeal(MealType.LUNCH, meal(MealType.LUNCH, 400, 2.00));
        day.setMeal(MealType.DINNER, meal(MealType.DINNER, 600, 5.00));
        assertEquals(1000, day.getCalories(), 1e-9);
        assertEquals(7.00, day.getCost(), 1e-9);

        day.clearMeal(MealType.LUNCH);
        assertEquals(600, day.getCalories(), 1e-9);
        assertEquals(5.00, day.getCost(), 1e-9);
    }

    @Test
    void nullArgumentsAreRejected() {
        DayPlan day = new DayPlan(1);

        assertThrows(NullPointerException.class, () -> day.setMeal(null, meal(MealType.LUNCH, 1, 1)));
        assertThrows(NullPointerException.class, () -> day.setMeal(MealType.LUNCH, null));
        assertThrows(NullPointerException.class, () -> day.getMeal(null));
        assertThrows(NullPointerException.class, () -> day.clearMeal(null));
    }
}
