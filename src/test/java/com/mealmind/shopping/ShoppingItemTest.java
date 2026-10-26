package com.mealmind.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.Unit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShoppingItemTest {

    private static ShoppingItem item(String name, double quantity, double cost, String note) {
        return new ShoppingItem(name, quantity, Unit.GRAM, cost, Aisle.PANTRY_STAPLES, note);
    }

    @Test
    void constructionTrimsName() {
        ShoppingItem rice = item("  Rice ", 200, 0.50, "Bulk is cheaper");

        assertEquals("Rice", rice.getName());
        assertEquals("rice", rice.normalizedName());
        assertEquals(200, rice.getQuantity());
        assertEquals(Unit.GRAM, rice.getUnit());
        assertEquals(0.50, rice.getEstimatedCost());
        assertEquals(Aisle.PANTRY_STAPLES, rice.getAisle());
        assertEquals(Optional.of("Bulk is cheaper"), rice.getNote());
    }

    @Test
    void rejectsInvalidQuantity() {
        assertThrows(IllegalArgumentException.class, () -> item("Rice", 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> item("Rice", -1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> item("Rice", Double.NaN, 1, null));
        assertThrows(IllegalArgumentException.class, () -> item("Rice", Double.POSITIVE_INFINITY, 1, null));
    }

    @Test
    void rejectsNegativeCostBlankNameAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> item("Rice", 1, -0.01, null));
        assertThrows(IllegalArgumentException.class, () -> item("  ", 1, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ShoppingItem("Rice", 1, null, 1, Aisle.PANTRY_STAPLES, null));
        assertThrows(IllegalArgumentException.class, () -> new ShoppingItem("Rice", 1, Unit.GRAM, 1, null, null));
    }

    @Test
    void nullNoteIsEmpty() {
        assertEquals(Optional.empty(), item("Rice", 1, 1, null).getNote());
    }

    @Test
    void equalsAndHashCode() {
        assertEquals(item("Rice", 200, 0.50, null), item("Rice", 200, 0.50, null));
        assertEquals(item("Rice", 200, 0.50, null).hashCode(), item("Rice", 200, 0.50, null).hashCode());
        assertNotEquals(item("Rice", 200, 0.50, null), item("Rice", 201, 0.50, null));
        assertNotEquals(item("Rice", 200, 0.50, null), item("Rice", 200, 0.50, "note"));
    }
}
