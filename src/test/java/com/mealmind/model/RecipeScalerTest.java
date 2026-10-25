package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeScalerTest {

    private static final double EPS = 1e-9;

    private static Ingredient chicken() {
        return new Ingredient("Chicken breast", 400, Unit.GRAM, 660, 124, 0, 14, 8.00);
    }

    private static Ingredient rice() {
        return new Ingredient("Basmati rice", 300, Unit.GRAM, 1050, 21, 234, 3, 1.50);
    }

    private static Ingredient saltToTaste() {
        return new Ingredient("Salt, to taste", 1, Unit.TEASPOON, 0, 0, 0, 0, 0.01);
    }

    private static Recipe recipe(int servings, Ingredient... ingredients) {
        return new Recipe("r1", "Chicken Rice", servings, 25, List.of("Cook rice", "Grill chicken"),
                List.of(ingredients));
    }

    private static void assertScaled(Ingredient original, Ingredient scaled, double factor) {
        assertEquals(original.getQuantity() * factor, scaled.getQuantity(), EPS);
        assertEquals(original.getCalories() * factor, scaled.getCalories(), EPS);
        assertEquals(original.getProteinG() * factor, scaled.getProteinG(), EPS);
        assertEquals(original.getCarbsG() * factor, scaled.getCarbsG(), EPS);
        assertEquals(original.getFatG() * factor, scaled.getFatG(), EPS);
        assertEquals(original.getCost() * factor, scaled.getCost(), EPS);
    }

    @Test
    void doublingServingsDoublesEveryIngredient() {
        Recipe original = recipe(2, chicken(), rice());
        Recipe scaled = RecipeScaler.scale(original, 4);

        assertEquals(4, scaled.getServings());
        assertScaled(chicken(), scaled.getIngredients().get(0), 2);
        assertScaled(rice(), scaled.getIngredients().get(1), 2);
        assertEquals(original.getCalories() * 2, scaled.getCalories(), EPS);
        assertEquals(original.getCost() * 2, scaled.getCost(), EPS);
    }

    @Test
    void halvingServingsHalvesEveryIngredient() {
        Recipe scaled = RecipeScaler.scale(recipe(4, chicken(), rice()), 2);

        assertEquals(2, scaled.getServings());
        assertScaled(chicken(), scaled.getIngredients().get(0), 0.5);
        assertScaled(rice(), scaled.getIngredients().get(1), 0.5);
    }

    @Test
    void perServingValuesAreUnchanged() {
        Recipe original = recipe(3, chicken(), rice());
        Recipe scaled = RecipeScaler.scale(original, 7);

        assertEquals(original.caloriesPerServing(), scaled.caloriesPerServing(), EPS);
        assertEquals(original.proteinPerServing(), scaled.proteinPerServing(), EPS);
        assertEquals(original.carbsPerServing(), scaled.carbsPerServing(), EPS);
        assertEquals(original.fatPerServing(), scaled.fatPerServing(), EPS);
        assertEquals(original.costPerServing(), scaled.costPerServing(), EPS);
    }

    @Test
    void sameServingsReturnsSameInstance() {
        Recipe original = recipe(2, chicken());

        assertSame(original, RecipeScaler.scale(original, 2));
    }

    @Test
    void rejectsNonPositiveTargetServings() {
        Recipe original = recipe(2, chicken());

        assertThrows(IllegalArgumentException.class, () -> RecipeScaler.scale(original, 0));
        assertThrows(IllegalArgumentException.class, () -> RecipeScaler.scale(original, -3));
    }

    @Test
    void preservesMetadataAndLeavesOriginalUnchanged() {
        Recipe original = recipe(2, chicken(), rice());
        Recipe scaled = RecipeScaler.scale(original, 6);

        assertEquals("r1", scaled.getId());
        assertEquals("Chicken Rice", scaled.getName());
        assertEquals(25, scaled.getPrepMinutes());
        assertEquals(List.of("Cook rice", "Grill chicken"), scaled.getInstructions());
        assertEquals(2, original.getServings());
        assertEquals(List.of(chicken(), rice()), original.getIngredients());
    }

    @Test
    void nonScalableIngredientsKeepQuantityAndNutrition() {
        Ingredient pinch = new Ingredient("Pinch of salt", 0.5, Unit.GRAM, 0, 0, 0, 0, 0.01);
        Ingredient garnish = new Ingredient("Parsley for garnish", 5, Unit.GRAM, 2, 0.2, 0.3, 0, 0.10);
        Recipe scaled = RecipeScaler.scale(recipe(2, saltToTaste(), pinch, garnish, rice()), 6);

        assertEquals(saltToTaste(), scaled.getIngredients().get(0));
        assertEquals(pinch, scaled.getIngredients().get(1));
        assertEquals(garnish, scaled.getIngredients().get(2));
        assertScaled(rice(), scaled.getIngredients().get(3), 3);
    }

    @Test
    void isScalableMatchesPhrasesCaseInsensitively() {
        assertFalse(RecipeScaler.isScalable(saltToTaste()));
        assertFalse(RecipeScaler.isScalable(new Ingredient("A DASH of hot sauce", 1, Unit.MILLILITER, 0, 0, 0, 0, 0)));
        assertFalse(RecipeScaler.isScalable(new Ingredient("Oil for frying", 50, Unit.MILLILITER, 400, 0, 0, 45, 0.3)));
        assertFalse(RecipeScaler.isScalable(new Ingredient("Water as needed", 100, Unit.MILLILITER, 0, 0, 0, 0, 0)));
        assertTrue(RecipeScaler.isScalable(rice()));
        assertTrue(RecipeScaler.isScalable(new Ingredient("Dashi stock", 250, Unit.MILLILITER, 5, 1, 0, 0, 0.8)));
    }

    @Test
    void roundForDisplayFollowsUnitRules() {
        assertEquals(0.25, RecipeScaler.roundForDisplay(0.3, Unit.CUP));
        assertEquals(0.5, RecipeScaler.roundForDisplay(0.4, Unit.TEASPOON));
        assertEquals(12, RecipeScaler.roundForDisplay(12.4, Unit.GRAM));
        assertEquals(7.5, RecipeScaler.roundForDisplay(7.46, Unit.GRAM));
        assertEquals(1.23, RecipeScaler.roundForDisplay(1.234, Unit.KILOGRAM));
        assertEquals(2.75, RecipeScaler.roundForDisplay(2.8, Unit.COUNT));
    }

    @Test
    void roundForDisplayRejectsInvalidQuantities() {
        assertThrows(IllegalArgumentException.class, () -> RecipeScaler.roundForDisplay(Double.NaN, Unit.GRAM));
        assertThrows(IllegalArgumentException.class, () -> RecipeScaler.roundForDisplay(-1, Unit.GRAM));
        assertThrows(IllegalArgumentException.class,
                () -> RecipeScaler.roundForDisplay(Double.POSITIVE_INFINITY, Unit.CUP));
    }
}
