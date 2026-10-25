package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.ChangeEvent;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReplaceMealCommandTest {

    private static Meal meal(String label) {
        Ingredient food = new Ingredient(label + " base", 100, Unit.GRAM, 300, 10, 30, 5, 2.00);
        return new Meal(MealType.DINNER, label, List.of(food));
    }

    @Test
    void replacingOccupiedSlotThenUndoRestoresOldMeal() {
        MealPlan plan = new MealPlan("Week", 2);
        Meal oldMeal = meal("Curry");
        Meal newMeal = meal("Pasta");
        plan.setMeal(2, MealType.DINNER, oldMeal);
        ReplaceMealCommand command = new ReplaceMealCommand(plan, 2, MealType.DINNER, newMeal);

        command.execute();
        assertSame(newMeal, plan.getMeal(2, MealType.DINNER).orElseThrow());

        command.undo();
        assertSame(oldMeal, plan.getMeal(2, MealType.DINNER).orElseThrow());

        command.execute();
        assertSame(newMeal, plan.getMeal(2, MealType.DINNER).orElseThrow());
        assertEquals("Replace Day 2 DINNER with Pasta", command.description());
    }

    @Test
    void fillingEmptySlotThenUndoLeavesItEmpty() {
        MealPlan plan = new MealPlan("Week", 1);
        ReplaceMealCommand command = new ReplaceMealCommand(plan, 1, MealType.DINNER, meal("Pasta"));

        command.execute();
        command.undo();

        assertEquals(Optional.empty(), plan.getMeal(1, MealType.DINNER));
    }

    @Test
    void planObserversAreNotifiedForExecuteAndUndo() {
        MealPlan plan = new MealPlan("Week", 1);
        List<ChangeEvent> events = new ArrayList<>();
        plan.addObserver(events::add);
        ReplaceMealCommand command = new ReplaceMealCommand(plan, 1, MealType.DINNER, meal("Pasta"));

        command.execute();
        command.undo();

        assertEquals(2, events.size());
        assertEquals("Day 1 DINNER set to Pasta", events.get(0).description());
        assertEquals("Day 1 DINNER cleared", events.get(1).description());
    }

    @Test
    void invalidArgumentsAreRejectedAtConstruction() {
        MealPlan plan = new MealPlan("Week", 2);

        assertThrows(IllegalArgumentException.class, () -> new ReplaceMealCommand(plan, 3, MealType.DINNER, meal("Pasta")));
        assertThrows(NullPointerException.class, () -> new ReplaceMealCommand(null, 1, MealType.DINNER, meal("Pasta")));
        assertThrows(NullPointerException.class, () -> new ReplaceMealCommand(plan, 1, null, meal("Pasta")));
        assertThrows(NullPointerException.class, () -> new ReplaceMealCommand(plan, 1, MealType.DINNER, null));
    }
}
