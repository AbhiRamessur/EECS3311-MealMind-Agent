package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservablePlanTest {

    private static Meal meal(MealType type, String label) {
        Ingredient food = new Ingredient("Food", 100, Unit.GRAM, 300, 10, 30, 5, 2.00);
        return new Meal(type, label, List.of(food));
    }

    @Test
    void planSetMealNotifiesOnce() {
        MealPlan plan = new MealPlan("Week", 2);
        List<ChangeEvent> events = new ArrayList<>();
        plan.addObserver(events::add);

        plan.setMeal(2, MealType.DINNER, meal(MealType.DINNER, "Pasta"));

        assertEquals(1, events.size());
        assertSame(plan, events.get(0).source());
        assertEquals("Day 2 DINNER set to Pasta", events.get(0).description());
    }

    @Test
    void planClearMealNotifiesOnceOnlyWhenSomethingWasRemoved() {
        MealPlan plan = new MealPlan("Week", 2);
        List<ChangeEvent> events = new ArrayList<>();
        plan.addObserver(events::add);

        plan.clearMeal(1, MealType.LUNCH);
        assertEquals(0, events.size());

        plan.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH, null));
        plan.clearMeal(1, MealType.LUNCH);
        assertEquals(2, events.size());
        assertEquals("Day 1 LUNCH cleared", events.get(1).description());
    }

    @Test
    void changeMadeThroughGetDayNotifiesPlanObserversOnce() {
        MealPlan plan = new MealPlan("Week", 2);
        List<ChangeEvent> events = new ArrayList<>();
        plan.addObserver(events::add);

        plan.getDay(1).setMeal(MealType.BREAKFAST, meal(MealType.BREAKFAST, null));

        assertEquals(1, events.size());
        assertSame(plan, events.get(0).source());
        assertEquals("Day 1 BREAKFAST set to Breakfast", events.get(0).description());
    }

    @Test
    void dayPlanAloneNotifiesItsOwnObservers() {
        DayPlan day = new DayPlan(3);
        List<ChangeEvent> events = new ArrayList<>();
        day.addObserver(events::add);

        day.setMeal(MealType.SNACK, meal(MealType.SNACK, "Apple"));
        day.clearMeal(MealType.SNACK);
        day.clearMeal(MealType.SNACK);

        assertEquals(2, events.size());
        assertSame(day, events.get(0).source());
        assertEquals("Day 3 SNACK set to Apple", events.get(0).description());
        assertEquals("Day 3 SNACK cleared", events.get(1).description());
    }
}
