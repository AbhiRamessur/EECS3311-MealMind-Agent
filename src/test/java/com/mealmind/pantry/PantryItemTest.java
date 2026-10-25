package com.mealmind.pantry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PantryItemTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Test
    void constructionTrimsName() {
        PantryItem item = new PantryItem("  Milk ", 1, Unit.LITER, TODAY);

        assertEquals("Milk", item.getName());
        assertEquals(1, item.getQuantity());
        assertEquals(Unit.LITER, item.getUnit());
        assertEquals(Optional.of(TODAY), item.getExpiry());
        assertEquals(Optional.empty(), new PantryItem("Salt", 500, Unit.GRAM).getExpiry());
    }

    @Test
    void rejectsNonPositiveOrNonFiniteQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new PantryItem("Milk", 0, Unit.LITER));
        assertThrows(IllegalArgumentException.class, () -> new PantryItem("Milk", -1, Unit.LITER));
        assertThrows(IllegalArgumentException.class, () -> new PantryItem("Milk", Double.NaN, Unit.LITER));
        assertThrows(IllegalArgumentException.class,
                () -> new PantryItem("Milk", Double.POSITIVE_INFINITY, Unit.LITER));
    }

    @Test
    void rejectsBlankNameAndNullUnit() {
        assertThrows(IllegalArgumentException.class, () -> new PantryItem("  ", 1, Unit.LITER));
        assertThrows(IllegalArgumentException.class, () -> new PantryItem(null, 1, Unit.LITER));
        assertThrows(IllegalArgumentException.class, () -> new PantryItem("Milk", 1, null));
    }

    @Test
    void normalizedNameIsCanonical() {
        assertEquals("chicken breast", new PantryItem("  Chicken   BREAST ", 500, Unit.GRAM).normalizedName());
    }

    @Test
    void withQuantityReturnsNewItem() {
        PantryItem original = new PantryItem("Milk", 1, Unit.LITER, TODAY);
        PantryItem changed = original.withQuantity(2);

        assertNotSame(original, changed);
        assertEquals(2, changed.getQuantity());
        assertEquals(Optional.of(TODAY), changed.getExpiry());
        assertEquals(1, original.getQuantity());
        assertThrows(IllegalArgumentException.class, () -> original.withQuantity(0));
    }

    @Test
    void daysUntilExpiryCanBePositiveZeroOrNegative() {
        assertEquals(3, new PantryItem("Milk", 1, Unit.LITER, TODAY.plusDays(3)).daysUntilExpiry(TODAY));
        assertEquals(0, new PantryItem("Milk", 1, Unit.LITER, TODAY).daysUntilExpiry(TODAY));
        assertEquals(-2, new PantryItem("Milk", 1, Unit.LITER, TODAY.minusDays(2)).daysUntilExpiry(TODAY));
    }

    @Test
    void expiryChecksWithoutExpiryDate() {
        PantryItem salt = new PantryItem("Salt", 500, Unit.GRAM);

        assertFalse(salt.isExpired(TODAY));
        assertThrows(IllegalStateException.class, () -> salt.daysUntilExpiry(TODAY));
    }

    @Test
    void isExpiredOnlyAfterExpiryDay() {
        assertFalse(new PantryItem("Milk", 1, Unit.LITER, TODAY).isExpired(TODAY));
        assertTrue(new PantryItem("Milk", 1, Unit.LITER, TODAY.minusDays(1)).isExpired(TODAY));
    }

    @Test
    void equalsAndHashCodeCoverAllFields() {
        PantryItem a = new PantryItem("Milk", 1, Unit.LITER, TODAY);
        PantryItem same = new PantryItem("Milk", 1, Unit.LITER, TODAY);

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, new PantryItem("Oat Milk", 1, Unit.LITER, TODAY));
        assertNotEquals(a, new PantryItem("Milk", 2, Unit.LITER, TODAY));
        assertNotEquals(a, new PantryItem("Milk", 1, Unit.MILLILITER, TODAY));
        assertNotEquals(a, new PantryItem("Milk", 1, Unit.LITER, TODAY.plusDays(1)));
        assertNotEquals(a, new PantryItem("Milk", 1, Unit.LITER));
    }
}
