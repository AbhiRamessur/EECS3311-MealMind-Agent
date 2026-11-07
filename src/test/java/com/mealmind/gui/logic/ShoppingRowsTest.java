package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import com.mealmind.shopping.Aisle;
import com.mealmind.shopping.ShoppingItem;
import com.mealmind.shopping.ShoppingList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShoppingRowsTest {

    private static final ShoppingItem FLOUR = new ShoppingItem("Flour", 1500, Unit.GRAM, 3.5, Aisle.PANTRY_STAPLES, null);
    private static final ShoppingItem MILK = new ShoppingItem("Milk", 1250, Unit.MILLILITER, 2.0, Aisle.DAIRY_EGGS, null);
    private static final ShoppingItem APPLES = new ShoppingItem("Apples", 3, Unit.COUNT, 1.25, Aisle.PRODUCE, "2 have expired");
    private static final ShoppingList LIST = new ShoppingList(List.of(FLOUR, MILK, APPLES), List.of("Pantry is empty: all ingredients are listed as missing."));

    @Test
    void rowsFollowTheListOrderWithCliAmountsAndCosts() {
        List<ShoppingRow> rows = ShoppingRows.of(LIST, Set.of());

        List<String> names = LIST.byAisle().values().stream().flatMap(List::stream).map(ShoppingItem::getName).toList();
        assertEquals(names, rows.stream().map(ShoppingRow::name).toList());
        ShoppingRow flour = rows.stream().filter(r -> r.name().equals("Flour")).findFirst().orElseThrow();
        assertEquals("1.5 kg", flour.quantityText());
        assertEquals("$3.50", flour.costText());
        assertEquals(Aisle.PANTRY_STAPLES.getDisplayName(), flour.aisle());
        assertEquals("1.25 l", rows.stream().filter(r -> r.name().equals("Milk")).findFirst().orElseThrow().quantityText());
        ShoppingRow apples = rows.stream().filter(r -> r.name().equals("Apples")).findFirst().orElseThrow();
        assertEquals("3 pc", apples.quantityText());
        assertEquals("2 have expired", apples.note());
        assertEquals("", flour.note());
    }

    @Test
    void checkedFlagsKeysAndRetainKnown() {
        String key = ShoppingRows.key(FLOUR);
        assertEquals("flour|g", key);
        assertEquals(key, ShoppingRows.key(new ShoppingItem("  FLOUR ", 2, Unit.GRAM, 0.1, Aisle.PANTRY_STAPLES, null)));

        List<ShoppingRow> rows = ShoppingRows.of(LIST, Set.of(key, "gone|g"));
        assertTrue(rows.stream().filter(r -> r.name().equals("Flour")).allMatch(ShoppingRow::checked));
        assertFalse(rows.stream().filter(r -> r.name().equals("Milk")).allMatch(ShoppingRow::checked));
        assertEquals(Set.of(key), ShoppingRows.retainKnown(LIST, Set.of(key, "gone|g")));
    }

    @Test
    void totalAndWarnings() {
        assertEquals("Estimated total: $6.75", ShoppingRows.totalText(LIST));
        assertEquals(List.of("Pantry is empty: all ingredients are listed as missing."), ShoppingRows.warnings(LIST));
        assertEquals("Estimated total: $0.00", ShoppingRows.totalText(ShoppingList.empty()));
        assertEquals(List.of(), ShoppingRows.of(ShoppingList.empty(), Set.of()));
    }
}
