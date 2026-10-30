package com.mealmind.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DietTypeTest {

    @Test
    void parseIgnoresCaseAndWhitespace() {
        assertEquals(DietType.VEGAN, DietType.parse(" Vegan "));
        assertEquals(DietType.PESCATARIAN, DietType.parse("PESCATARIAN"));
        assertEquals("Vegetarian", DietType.VEGETARIAN.getDisplayName());
    }

    @Test
    void parseRejectsUnknownAndNullListingValidValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DietType.parse("keto"));
        assertTrue(e.getMessage().contains("omnivore, vegetarian, vegan, pescatarian"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> DietType.parse(null));
        assertThrows(IllegalArgumentException.class, () -> DietType.parse(" "));
    }
}
