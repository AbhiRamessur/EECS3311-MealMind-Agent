package com.mealmind.nutrition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Unit;
import org.junit.jupiter.api.Test;

class NutritionDataTest {

    private static Ingredient zero(String name) {
        return new Ingredient(name, 10, Unit.GRAM, 0, 0, 0, 0, 0.10);
    }

    @Test
    void zeroEverythingIsUnknown() {
        assertTrue(NutritionData.isUnknown(zero("Mystery sauce")));
    }

    @Test
    void waterSaltAndPepperAreKnownZero() {
        for (String name : new String[] {"Water", "Salt", "Black Pepper", "white pepper", "Pinch of salt",
                "Salt, to taste", "Ice", "Baking soda", "Baking Powder", "Apple cider vinegar"}) {
            assertFalse(NutritionData.isUnknown(zero(name)), name);
        }
    }

    @Test
    void spicesAndSeasoningsAreKnownZero() {
        assertFalse(NutritionData.isUnknown(zero("Mixed spices")));
        assertFalse(NutritionData.isUnknown(zero("Taco seasoning")));
        assertFalse(NutritionData.isUnknown(zero("Spice blend")));
    }

    @Test
    void nonZeroIngredientIsNeverUnknown() {
        assertFalse(NutritionData.isUnknown(new Ingredient("Mystery sauce", 10, Unit.GRAM, 0, 0, 1, 0, 0)));
        assertFalse(NutritionData.isUnknown(new Ingredient("Rice", 100, Unit.GRAM, 130, 3, 28, 0, 0.5)));
    }

    @Test
    void matchingUsesWholeWords() {
        assertTrue(NutritionData.isUnknown(zero("Saltine crackers")));
        assertTrue(NutritionData.isUnknown(zero("Iceberg lettuce")));
        assertTrue(NutritionData.isUnknown(zero("Spicy mayo")));
        assertThrows(NullPointerException.class, () -> NutritionData.isUnknown(null));
    }
}
