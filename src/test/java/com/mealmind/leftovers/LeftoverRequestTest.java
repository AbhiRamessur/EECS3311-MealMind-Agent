package com.mealmind.leftovers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LeftoverRequestTest {

    @Test
    void defaultsAreThreeDaysThreeMealsNoOverwrite() {
        LeftoverRequest defaults = LeftoverRequest.defaults();
        assertEquals(3, defaults.withinDays());
        assertEquals(3, defaults.meals());
        assertFalse(defaults.overwrite());
    }

    @Test
    void boundsAreInclusive() {
        assertEquals(1, new LeftoverRequest(1, 1, false).withinDays());
        LeftoverRequest max = new LeftoverRequest(14, 6, true);
        assertEquals(14, max.withinDays());
        assertEquals(6, max.meals());
        assertTrue(max.overwrite());
    }

    @Test
    void outOfRangeValuesAreRejectedWithClearMessages() {
        assertEquals("Days must be from 1 to 14, got 0",
                assertThrows(IllegalArgumentException.class, () -> new LeftoverRequest(0, 3, false)).getMessage());
        assertEquals("Days must be from 1 to 14, got 15",
                assertThrows(IllegalArgumentException.class, () -> new LeftoverRequest(15, 3, false)).getMessage());
        assertEquals("Meals must be from 1 to 6, got 0",
                assertThrows(IllegalArgumentException.class, () -> new LeftoverRequest(3, 0, false)).getMessage());
        assertEquals("Meals must be from 1 to 6, got 7",
                assertThrows(IllegalArgumentException.class, () -> new LeftoverRequest(3, 7, false)).getMessage());
    }
}
