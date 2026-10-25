package com.mealmind.pantry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.ChangeEvent;
import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Unit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PantryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private Pantry pantry;
    private List<ChangeEvent> events;

    @BeforeEach
    void setUp() {
        pantry = new Pantry();
        events = new ArrayList<>();
        pantry.addObserver(events::add);
    }

    private static PantryItem item(String name, double quantity, Unit unit) {
        return new PantryItem(name, quantity, unit);
    }

    private static PantryItem item(String name, double quantity, Unit unit, LocalDate expiry) {
        return new PantryItem(name, quantity, unit, expiry);
    }

    @Test
    void addingNewItemReturnsEmpty() {
        assertEquals(Optional.empty(), pantry.addItem(item("Rice", 1, Unit.KILOGRAM)));

        assertEquals(1, pantry.size());
        assertEquals(1, events.size());
    }

    @Test
    void namesThatNormalizeEquallyAreOneEntry() {
        pantry.addItem(item("Chicken Breast", 500, Unit.GRAM));
        pantry.addItem(item(" chicken  breast ", 200, Unit.GRAM));

        assertEquals(1, pantry.size());
        assertEquals(700, pantry.getItem("CHICKEN BREAST").orElseThrow().getQuantity(), 1e-9);
    }

    @Test
    void mergeConvertsIntoExistingUnitAndReturnsPreviousItem() {
        PantryItem first = item("Chicken Breast", 500, Unit.GRAM);
        pantry.addItem(first);

        Optional<PantryItem> previous = pantry.addItem(item("chicken breast", 1, Unit.KILOGRAM));

        assertEquals(Optional.of(first), previous);
        PantryItem merged = pantry.getItem("chicken breast").orElseThrow();
        assertEquals(1500, merged.getQuantity(), 1e-9);
        assertEquals(Unit.GRAM, merged.getUnit());
        assertEquals("Chicken Breast", merged.getName());
    }

    @Test
    void mergeLitersAndMilliliters() {
        pantry.addItem(item("Milk", 1, Unit.LITER));
        pantry.addItem(item("MILK", 500, Unit.MILLILITER));

        PantryItem merged = pantry.getItem("milk").orElseThrow();
        assertEquals(1.5, merged.getQuantity(), 1e-9);
        assertEquals(Unit.LITER, merged.getUnit());
        assertEquals("Milk", merged.getName());
    }

    @Test
    void mergeKeepsEarlierExpiryOrTheOnlyOne() {
        pantry.addItem(item("Milk", 1, Unit.LITER, TODAY.plusDays(5)));
        pantry.addItem(item("Milk", 1, Unit.LITER, TODAY.plusDays(2)));
        assertEquals(Optional.of(TODAY.plusDays(2)), pantry.getItem("milk").orElseThrow().getExpiry());

        pantry.addItem(item("Milk", 1, Unit.LITER, TODAY.plusDays(9)));
        assertEquals(Optional.of(TODAY.plusDays(2)), pantry.getItem("milk").orElseThrow().getExpiry());

        pantry.addItem(item("Eggs", 6, Unit.COUNT));
        pantry.addItem(item("Eggs", 6, Unit.COUNT, TODAY.plusDays(14)));
        assertEquals(Optional.of(TODAY.plusDays(14)), pantry.getItem("eggs").orElseThrow().getExpiry());

        pantry.addItem(item("Eggs", 6, Unit.COUNT));
        assertEquals(Optional.of(TODAY.plusDays(14)), pantry.getItem("eggs").orElseThrow().getExpiry());
    }

    @Test
    void incompatibleAddLeavesPantryUnchangedAndDoesNotNotify() {
        PantryItem flour = item("Flour", 500, Unit.GRAM);
        pantry.addItem(flour);
        events.clear();

        assertThrows(IncompatibleUnitException.class, () -> pantry.addItem(item("Flour", 1, Unit.LITER)));

        assertEquals(List.of(flour), pantry.getItems());
        assertEquals(0, events.size());
    }

    @Test
    void removeItemReturnsItemThenEmptyAndNotifiesOnlyOnce() {
        PantryItem rice = item("Rice", 1, Unit.KILOGRAM);
        pantry.addItem(rice);
        events.clear();

        assertEquals(Optional.of(rice), pantry.removeItem(" RICE "));
        assertEquals(Optional.empty(), pantry.removeItem("rice"));

        assertTrue(pantry.isEmpty());
        assertEquals(1, events.size());
    }

    @Test
    void replaceItemOverwritesExactlyAndNotifies() {
        pantry.addItem(item("Rice", 1, Unit.KILOGRAM, TODAY.plusDays(30)));
        events.clear();
        PantryItem replacement = item("rice", 250, Unit.GRAM);

        pantry.replaceItem(replacement);

        assertEquals(List.of(replacement), pantry.getItems());
        assertEquals(1, events.size());
    }

    @Test
    void getItemsIsSortedAndUnmodifiable() {
        pantry.addItem(item("Tomatoes", 4, Unit.COUNT));
        pantry.addItem(item("apples", 3, Unit.COUNT));
        pantry.addItem(item("Bread", 1, Unit.COUNT));

        List<PantryItem> items = pantry.getItems();

        assertEquals(List.of("apples", "Bread", "Tomatoes"), items.stream().map(PantryItem::getName).toList());
        assertThrows(UnsupportedOperationException.class, () -> items.add(item("Salt", 1, Unit.GRAM)));
    }

    @Test
    void availableQuantityConvertsOrReturnsZero() {
        pantry.addItem(item("Chicken Breast", 1500, Unit.GRAM));

        assertEquals(1.5, pantry.availableQuantity("chicken breast", Unit.KILOGRAM), 1e-9);
        assertEquals(0, pantry.availableQuantity("tofu", Unit.GRAM));
        assertThrows(IncompatibleUnitException.class, () -> pantry.availableQuantity("chicken breast", Unit.CUP));
    }

    @Test
    void getExpiringItemsIncludesExpiredAndBoundaryItemsSorted() {
        pantry.addItem(item("Yogurt", 1, Unit.COUNT, TODAY.plusDays(3)));
        pantry.addItem(item("Milk", 1, Unit.LITER, TODAY.minusDays(1)));
        pantry.addItem(item("Bread", 1, Unit.COUNT, TODAY.plusDays(3)));
        pantry.addItem(item("Cheese", 200, Unit.GRAM, TODAY.plusDays(4)));
        pantry.addItem(item("Salt", 500, Unit.GRAM));

        List<PantryItem> expiring = pantry.getExpiringItems(TODAY, 3);

        assertEquals(List.of("Milk", "Bread", "Yogurt"), expiring.stream().map(PantryItem::getName).toList());
        assertThrows(IllegalArgumentException.class, () -> pantry.getExpiringItems(TODAY, -1));
    }

    @Test
    void eachSuccessfulMutationNotifiesExactlyOnce() {
        pantry.addItem(item("Rice", 1, Unit.KILOGRAM));
        pantry.addItem(item("Rice", 500, Unit.GRAM));
        pantry.replaceItem(item("Rice", 2, Unit.KILOGRAM));
        pantry.removeItem("rice");

        assertEquals(4, events.size());
        assertEquals("Added Rice", events.get(0).description());
        assertEquals("Merged Rice into Rice", events.get(1).description());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> pantry.addItem(null));
        assertThrows(NullPointerException.class, () -> pantry.removeItem(null));
        assertThrows(NullPointerException.class, () -> pantry.replaceItem(null));
        assertThrows(NullPointerException.class, () -> pantry.getItem(null));
        assertThrows(NullPointerException.class, () -> pantry.availableQuantity(null, Unit.GRAM));
        assertThrows(NullPointerException.class, () -> pantry.availableQuantity("rice", null));
        assertThrows(NullPointerException.class, () -> pantry.getExpiringItems(null, 1));
    }
}
