package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientTest {

    private static Ingredient rice() {
        return new Ingredient("Rice", 200, Unit.GRAM, 260, 5, 56, 1, 0.80);
    }

    @Test
    void constructionTrimsName() {
        Ingredient ingredient = new Ingredient("  Rice  ", 200, Unit.GRAM, 260, 5, 56, 1, 0.80);

        assertEquals("Rice", ingredient.getName());
        assertEquals(200, ingredient.getQuantity());
        assertEquals(Unit.GRAM, ingredient.getUnit());
    }

    @Test
    void rejectsBlankOrNullName() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("  ", 1, Unit.GRAM, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Ingredient(null, 1, Unit.GRAM, 0, 0, 0, 0, 0));
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("Rice", -1, Unit.GRAM, 0, 0, 0, 0, 0));
    }

    @Test
    void rejectsNegativeCalories() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("Rice", 1, Unit.GRAM, -5, 0, 0, 0, 0));
    }

    @Test
    void rejectsNanCost() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ingredient("Rice", 1, Unit.GRAM, 0, 0, 0, 0, Double.NaN));
    }

    @Test
    void rejectsNullUnit() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("Rice", 1, null, 0, 0, 0, 0, 0));
    }

    @Test
    void normalizedNameIsLowercaseTrimmedAndCollapsed() {
        Ingredient ingredient = new Ingredient("  Chicken   BREAST ", 500, Unit.GRAM, 0, 0, 0, 0, 0);

        assertEquals("chicken breast", ingredient.normalizedName());
    }

    @Test
    void withQuantityDoublesAllTotals() {
        Ingredient doubled = rice().withQuantity(400);

        assertEquals(400, doubled.getQuantity());
        assertEquals(520, doubled.getCalories(), 1e-9);
        assertEquals(10, doubled.getProteinG(), 1e-9);
        assertEquals(112, doubled.getCarbsG(), 1e-9);
        assertEquals(2, doubled.getFatG(), 1e-9);
        assertEquals(1.60, doubled.getCost(), 1e-9);
    }

    @Test
    void withQuantityHalvesAllTotals() {
        Ingredient halved = rice().withQuantity(100);

        assertEquals(100, halved.getQuantity());
        assertEquals(130, halved.getCalories(), 1e-9);
        assertEquals(2.5, halved.getProteinG(), 1e-9);
        assertEquals(28, halved.getCarbsG(), 1e-9);
        assertEquals(0.5, halved.getFatG(), 1e-9);
        assertEquals(0.40, halved.getCost(), 1e-9);
    }

    @Test
    void withQuantityLeavesOriginalUnchanged() {
        Ingredient original = rice();
        original.withQuantity(400);

        assertEquals(rice(), original);
    }

    @Test
    void withQuantityFromZero() {
        Ingredient empty = new Ingredient("Rice", 0, Unit.GRAM, 0, 0, 0, 0, 0);

        assertThrows(IllegalStateException.class, () -> empty.withQuantity(100));
        assertEquals(empty, empty.withQuantity(0));
    }

    @Test
    void convertToKilogramsKeepsTotals() {
        Ingredient grams = new Ingredient("Chicken Breast", 500, Unit.GRAM, 825, 155, 0, 18, 6.50);
        Ingredient kilograms = grams.convertTo(Unit.KILOGRAM);

        assertEquals(0.5, kilograms.getQuantity(), 1e-9);
        assertEquals(Unit.KILOGRAM, kilograms.getUnit());
        assertEquals(825, kilograms.getCalories());
        assertEquals(6.50, kilograms.getCost());
    }

    @Test
    void convertToAcrossDimensionsThrows() {
        assertThrows(IncompatibleUnitException.class, () -> rice().convertTo(Unit.LITER));
    }

    @Test
    void equalIngredientsHaveEqualHashCodes() {
        assertEquals(rice(), rice());
        assertEquals(rice().hashCode(), rice().hashCode());
    }

    @Test
    void ingredientsDifferWhenAnyFieldDiffers() {
        List<Ingredient> variants = List.of(
                new Ingredient("Brown Rice", 200, Unit.GRAM, 260, 5, 56, 1, 0.80),
                new Ingredient("Rice", 250, Unit.GRAM, 260, 5, 56, 1, 0.80),
                new Ingredient("Rice", 200, Unit.MILLILITER, 260, 5, 56, 1, 0.80),
                new Ingredient("Rice", 200, Unit.GRAM, 261, 5, 56, 1, 0.80),
                new Ingredient("Rice", 200, Unit.GRAM, 260, 6, 56, 1, 0.80),
                new Ingredient("Rice", 200, Unit.GRAM, 260, 5, 57, 1, 0.80),
                new Ingredient("Rice", 200, Unit.GRAM, 260, 5, 56, 2, 0.80),
                new Ingredient("Rice", 200, Unit.GRAM, 260, 5, 56, 1, 0.90));

        for (Ingredient variant : variants) {
            assertNotEquals(rice(), variant, variant.toString());
        }
    }
}
