package com.mealmind.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AisleTest {

    @Test
    void classifiesCommonGroceries() {
        assertEquals(Aisle.MEAT_SEAFOOD, Aisle.classify("Chicken Breast"));
        assertEquals(Aisle.PRODUCE, Aisle.classify("  Roma   TOMATOES "));
        assertEquals(Aisle.DAIRY_EGGS, Aisle.classify("Large eggs"));
        assertEquals(Aisle.BAKERY, Aisle.classify("Whole wheat bread"));
        assertEquals(Aisle.PANTRY_STAPLES, Aisle.classify("Salt, to taste"));
    }

    @Test
    void longestKeywordWins() {
        assertEquals(Aisle.PANTRY_STAPLES, Aisle.classify("peanut butter"));
        assertEquals(Aisle.DAIRY_EGGS, Aisle.classify("butter"));
        assertEquals(Aisle.PANTRY_STAPLES, Aisle.classify("Extra virgin olive oil"));
        assertEquals(Aisle.PANTRY_STAPLES, Aisle.classify("Coconut milk"));
        assertEquals(Aisle.FROZEN, Aisle.classify("frozen peas"));
        assertEquals(Aisle.FROZEN, Aisle.classify("Frozen spinach"));
    }

    @Test
    void unknownNamesAreOther() {
        assertEquals(Aisle.OTHER, Aisle.classify("dragonfruit sorbet"));
    }

    @Test
    void matchesWholeWordsOnly() {
        // "apple" is a PRODUCE keyword and "egg" a DAIRY_EGGS keyword, but neither is a whole word here.
        assertEquals(Aisle.OTHER, Aisle.classify("Pineapple"));
        assertNotEquals(Aisle.DAIRY_EGGS, Aisle.classify("Eggplant"));
    }

    @Test
    void displayOrderIsEnumOrder() {
        assertEquals(List.of("Produce", "Meat & Seafood", "Dairy & Eggs", "Bakery", "Pantry Staples", "Frozen", "Other"),
                List.of(Aisle.values()).stream().map(Aisle::getDisplayName).toList());
    }
}
