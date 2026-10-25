package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeTest {

    private static Ingredient chicken() {
        return new Ingredient("Chicken", 100, Unit.GRAM, 100, 20, 0, 2, 2.00);
    }

    private static Ingredient rice() {
        return new Ingredient("Rice", 100, Unit.GRAM, 200, 4, 45, 1, 0.50);
    }

    private static Recipe recipe(String id, String name, int servings, int prepMinutes, List<Ingredient> ingredients) {
        return new Recipe(id, name, servings, prepMinutes, List.of(), ingredients);
    }

    @Test
    void constructionTrimsIdAndName() {
        Recipe recipe = new Recipe("  r1 ", "  Chicken and Rice ", 2, 20, List.of("Cook"), List.of(chicken()));

        assertEquals("r1", recipe.getId());
        assertEquals("Chicken and Rice", recipe.getName());
        assertEquals(2, recipe.getServings());
        assertEquals(20, recipe.getPrepMinutes());
        assertEquals(List.of("Cook"), recipe.getInstructions());
    }

    @Test
    void rejectsInvalidArguments() {
        List<Ingredient> ok = List.of(chicken());

        assertThrows(IllegalArgumentException.class, () -> recipe(" ", "Name", 1, 0, ok));
        assertThrows(IllegalArgumentException.class, () -> recipe("r1", " ", 1, 0, ok));
        assertThrows(IllegalArgumentException.class, () -> recipe("r1", "Name", 0, 0, ok));
        assertThrows(IllegalArgumentException.class, () -> recipe("r1", "Name", 1, -1, ok));
        assertThrows(IllegalArgumentException.class, () -> recipe("r1", "Name", 1, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> recipe("r1", "Name", 1, 0, null));
    }

    @Test
    void perServingValuesDivideTotalsByServings() {
        // 2 rice + 2 chicken = 600 cal, 48 g protein, 90 g carbs, 6 g fat, 5.00 cost, over 3 servings.
        Recipe recipe = recipe("r1", "Big Bowl", 3, 15, List.of(rice(), rice(), chicken(), chicken()));

        assertEquals(600, recipe.getCalories(), 1e-9);
        assertEquals(200, recipe.caloriesPerServing(), 1e-9);
        assertEquals(16, recipe.proteinPerServing(), 1e-9);
        assertEquals(30, recipe.carbsPerServing(), 1e-9);
        assertEquals(2, recipe.fatPerServing(), 1e-9);
        assertEquals(5.00 / 3, recipe.costPerServing(), 1e-9);
    }

    @Test
    void listsAreDefensivelyCopied() {
        List<String> steps = new ArrayList<>(List.of("Cook"));
        List<Ingredient> ingredients = new ArrayList<>(List.of(chicken()));
        Recipe recipe = new Recipe("r1", "Chicken", 1, 5, steps, ingredients);

        steps.add("Serve");
        ingredients.add(rice());

        assertEquals(List.of("Cook"), recipe.getInstructions());
        assertEquals(List.of(chicken()), recipe.getIngredients());
    }

    @Test
    void listsAreUnmodifiable() {
        Recipe recipe = new Recipe("r1", "Chicken", 1, 5, List.of("Cook"), List.of(chicken()));

        assertThrows(UnsupportedOperationException.class, () -> recipe.getInstructions().add("Serve"));
        assertThrows(UnsupportedOperationException.class, () -> recipe.getIngredients().add(rice()));
    }

    @Test
    void equalityIsById() {
        Recipe a = recipe("r1", "Chicken", 1, 5, List.of(chicken()));
        Recipe sameId = recipe("r1", "Different Name", 4, 30, List.of(rice()));
        Recipe otherId = recipe("r2", "Chicken", 1, 5, List.of(chicken()));

        assertEquals(a, sameId);
        assertEquals(a.hashCode(), sameId.hashCode());
        assertNotEquals(a, otherId);
    }
}
