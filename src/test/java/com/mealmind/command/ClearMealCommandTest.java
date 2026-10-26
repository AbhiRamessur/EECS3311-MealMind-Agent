package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Unit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClearMealCommandTest {

    private static Meal meal() {
        Ingredient rice = new Ingredient("Rice", 200, Unit.GRAM, 260, 5, 56, 1, 0.50);
        return new Meal(MealType.LUNCH, "Rice Bowl", List.of(rice));
    }

    @Test
    void executeClearsAndUndoRestoresTheSameMeal() {
        MealPlan plan = new MealPlan("Week", 2);
        Meal lunch = meal();
        plan.setMeal(2, MealType.LUNCH, lunch);
        ClearMealCommand command = new ClearMealCommand(plan, 2, MealType.LUNCH);

        command.execute();
        assertEquals(Optional.empty(), plan.getMeal(2, MealType.LUNCH));

        command.undo();
        assertSame(lunch, plan.getMeal(2, MealType.LUNCH).orElseThrow());
        assertEquals("Clear Day 2 LUNCH", command.description());
    }

    @Test
    void clearingEmptySlotThrowsAndStaysNotExecuted() {
        MealPlan plan = new MealPlan("Week", 1);
        ClearMealCommand command = new ClearMealCommand(plan, 1, MealType.DINNER);

        assertThrows(NoSuchElementException.class, command::execute);
        assertThrows(IllegalStateException.class, command::undo);
    }

    @Test
    void invalidArgumentsAreRejectedAtConstruction() {
        MealPlan plan = new MealPlan("Week", 1);

        assertThrows(IllegalArgumentException.class, () -> new ClearMealCommand(plan, 2, MealType.DINNER));
        assertThrows(NullPointerException.class, () -> new ClearMealCommand(null, 1, MealType.DINNER));
        assertThrows(NullPointerException.class, () -> new ClearMealCommand(plan, 1, null));
    }
}
