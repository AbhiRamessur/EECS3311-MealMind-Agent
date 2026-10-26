package com.mealmind.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShoppingListTest {

    private static ShoppingItem item(String name, Unit unit, double cost, Aisle aisle) {
        return new ShoppingItem(name, 1, unit, cost, aisle, null);
    }

    @Test
    void itemsAreSortedByAisleThenNameThenUnit() {
        ShoppingItem rice = item("Rice", Unit.GRAM, 1, Aisle.PANTRY_STAPLES);
        ShoppingItem eggsCount = item("Eggs", Unit.COUNT, 2, Aisle.DAIRY_EGGS);
        ShoppingItem eggsGrams = item("Eggs", Unit.GRAM, 1, Aisle.DAIRY_EGGS);
        ShoppingItem apples = item("apples", Unit.COUNT, 3, Aisle.PRODUCE);
        ShoppingItem butter = item("Butter", Unit.GRAM, 4, Aisle.DAIRY_EGGS);

        ShoppingList list = new ShoppingList(List.of(rice, eggsCount, butter, apples, eggsGrams), List.of());

        assertEquals(List.of(apples, butter, eggsGrams, eggsCount, rice), list.getItems());
    }

    @Test
    void byAisleHasOnlyNonEmptyAislesInOrder() {
        ShoppingItem rice = item("Rice", Unit.GRAM, 1, Aisle.PANTRY_STAPLES);
        ShoppingItem apples = item("Apples", Unit.COUNT, 3, Aisle.PRODUCE);

        ShoppingList list = new ShoppingList(List.of(rice, apples), List.of());

        assertEquals(List.of(Aisle.PRODUCE, Aisle.PANTRY_STAPLES), List.copyOf(list.byAisle().keySet()));
        assertEquals(List.of(apples), list.byAisle().get(Aisle.PRODUCE));
    }

    @Test
    void totalCostSumsItems() {
        ShoppingList list = new ShoppingList(List.of(
                item("Rice", Unit.GRAM, 0.50, Aisle.PANTRY_STAPLES),
                item("Chicken", Unit.GRAM, 4.50, Aisle.MEAT_SEAFOOD)), List.of("note"));

        assertEquals(5.00, list.getTotalEstimatedCost(), 1e-9);
        assertEquals(2, list.size());
        assertEquals(List.of("note"), list.getWarnings());
    }

    @Test
    void listsAreUnmodifiable() {
        ShoppingItem rice = item("Rice", Unit.GRAM, 1, Aisle.PANTRY_STAPLES);
        ShoppingList list = new ShoppingList(List.of(rice), List.of("warning"));

        assertThrows(UnsupportedOperationException.class, () -> list.getItems().add(rice));
        assertThrows(UnsupportedOperationException.class, () -> list.getWarnings().add("more"));
        assertThrows(UnsupportedOperationException.class, () -> list.byAisle().put(Aisle.OTHER, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> list.byAisle().get(Aisle.PANTRY_STAPLES).add(rice));
    }

    @Test
    void emptyListIsEmpty() {
        ShoppingList empty = ShoppingList.empty();

        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getTotalEstimatedCost());
        assertTrue(empty.byAisle().isEmpty());
        assertEquals(new ShoppingList(List.of(), List.of()), empty);
    }
}
