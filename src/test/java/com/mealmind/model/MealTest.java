package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MealTest {

    private static Ingredient chicken() {
        return new Ingredient("Chicken", 100, Unit.GRAM, 100, 20, 0, 2, 2.00);
    }

    private static Ingredient rice() {
        return new Ingredient("Rice", 100, Unit.GRAM, 200, 4, 45, 1, 0.50);
    }

    private static Recipe riceBowl() {
        return new Recipe("r2", "Rice Bowl", 1, 10, List.of(), List.of(rice()));
    }

    @Test
    void labelDefaultsToTypeDisplayName() {
        assertEquals("Dinner", new Meal(MealType.DINNER, null, List.of(chicken())).getName());
        assertEquals("Dinner", new Meal(MealType.DINNER, "  ", List.of(chicken())).getName());
        assertEquals("Date night", new Meal(MealType.DINNER, " Date night ", List.of(chicken())).getName());
    }

    @Test
    void rejectsEmptyOrNullComponents() {
        assertThrows(IllegalArgumentException.class, () -> new Meal(MealType.LUNCH, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Meal(MealType.LUNCH, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Meal(null, null, List.of(chicken())));
    }

    @Test
    void totalsSumRecipeAndLooseIngredient() {
        Meal meal = new Meal(MealType.LUNCH, null, List.of(riceBowl(), chicken()));

        assertEquals(300, meal.getCalories(), 1e-9);
        assertEquals(24, meal.getProteinG(), 1e-9);
        assertEquals(45, meal.getCarbsG(), 1e-9);
        assertEquals(3, meal.getFatG(), 1e-9);
        assertEquals(2.50, meal.getCost(), 1e-9);
        assertEquals(MealType.LUNCH, meal.getType());
        assertEquals(2, meal.getComponents().size());
    }
}
