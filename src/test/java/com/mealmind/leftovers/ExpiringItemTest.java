package com.mealmind.leftovers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpiringItemTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private static ExpiringItem dueIn(long days) {
        return ExpiringItem.of(new PantryItem("Red lentils", 400, Unit.GRAM, TODAY.plusDays(days)), TODAY);
    }

    @Test
    void weightsFollowUrgency() {
        assertEquals(4, dueIn(-3).weight());
        assertEquals(4, dueIn(0).weight());
        assertEquals(3, dueIn(1).weight());
        assertEquals(2, dueIn(2).weight());
        assertEquals(1, dueIn(3).weight());
        assertEquals(1, dueIn(10).weight());
    }

    @Test
    void labelsDescribeEachCase() {
        assertEquals("Red lentils (expires today)", dueIn(0).label());
        assertEquals("Red lentils (expires in 1 day)", dueIn(1).label());
        assertEquals("Red lentils (expires in 3 days)", dueIn(3).label());
        assertEquals("Red lentils (expired 1 day ago)", dueIn(-1).label());
        assertEquals("Red lentils (expired 4 days ago)", dueIn(-4).label());
    }

    @Test
    void fieldsComeFromThePantryItem() {
        ExpiringItem expired = dueIn(-2);
        assertEquals("Red lentils", expired.name());
        assertEquals("red lentils", expired.normalizedName());
        assertEquals(-2, expired.daysUntilExpiry());
        assertTrue(expired.expired());
        assertFalse(dueIn(0).expired());
    }

    @Test
    void itemWithoutExpiryIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ExpiringItem.of(new PantryItem("Salt", 1, Unit.KILOGRAM), TODAY));
        assertEquals("Pantry item 'Salt' has no expiry date", e.getMessage());
    }
}
