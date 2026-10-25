package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MealTypeTest {

    @Test
    void parseIgnoresCaseAndWhitespace() {
        assertEquals(MealType.DINNER, MealType.parse("dinner"));
        assertEquals(MealType.LUNCH, MealType.parse(" LUNCH "));
    }

    @Test
    void parseRejectsUnknownAndNull() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MealType.parse("brunch"));
        assertEquals("Unknown meal type 'brunch'. Use one of: breakfast, lunch, dinner, snack", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> MealType.parse(null));
    }

    @Test
    void hasReadableDisplayNames() {
        assertEquals("Breakfast", MealType.BREAKFAST.getDisplayName());
        assertEquals("Snack", MealType.SNACK.getDisplayName());
    }
}
