package com.mealmind.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShoppingListFormatterTest {

    @Test
    void formatsFullListExactly() {
        ShoppingList list = new ShoppingList(List.of(
                new ShoppingItem("Rice", 1.5, Unit.KILOGRAM, 3, Aisle.PANTRY_STAPLES,
                        "Needed in several incompatible units; listed separately"),
                new ShoppingItem("Chicken Breast", 500, Unit.GRAM, 7.5, Aisle.MEAT_SEAFOOD, null)),
                List.of("Pantry is empty: all ingredients are listed as missing."));

        String expected = """
                MealMind Shopping List
                ! Pantry is empty: all ingredients are listed as missing.

                Meat & Seafood:
                - 500 g Chicken Breast (~$7.50)

                Pantry Staples:
                - 1.5 kg Rice (~$3.00) [Needed in several incompatible units; listed separately]

                Estimated total: $10.50
                """;
        assertEquals(expected, ShoppingListFormatter.toText(list));
    }

    @Test
    void emptyListSaysNothingToBuy() {
        String expected = """
                MealMind Shopping List
                Nothing to buy.

                Estimated total: $0.00
                """;
        assertEquals(expected, ShoppingListFormatter.toText(ShoppingList.empty()));
    }

    @Test
    void smallQuantitiesAreNeverShownAsZero() {
        ShoppingList list = new ShoppingList(List.of(
                new ShoppingItem("Cumin", 0.1, Unit.TEASPOON, 0.05, Aisle.PANTRY_STAPLES, null)), List.of());

        assertTrue(ShoppingListFormatter.toText(list).contains("- 0.1 tsp Cumin (~$0.05)\n"));
    }

    @Test
    void displayRoundingDoesNotChangeStoredValues() {
        ShoppingItem flour = new ShoppingItem("Flour", 512.4, Unit.GRAM, 1.234, Aisle.PANTRY_STAPLES, null);
        ShoppingList list = new ShoppingList(List.of(flour), List.of());

        assertTrue(ShoppingListFormatter.toText(list).contains("- 512 g Flour (~$1.23)\n"));
        assertEquals(512.4, list.getItems().get(0).getQuantity());
    }
}
