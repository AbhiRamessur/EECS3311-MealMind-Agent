package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UnitTest {

    @Test
    void eachUnitHasExpectedDimension() {
        assertEquals(Dimension.MASS, Unit.GRAM.getDimension());
        assertEquals(Dimension.MASS, Unit.KILOGRAM.getDimension());
        assertEquals(Dimension.VOLUME, Unit.MILLILITER.getDimension());
        assertEquals(Dimension.VOLUME, Unit.LITER.getDimension());
        assertEquals(Dimension.VOLUME, Unit.TEASPOON.getDimension());
        assertEquals(Dimension.VOLUME, Unit.TABLESPOON.getDimension());
        assertEquals(Dimension.VOLUME, Unit.CUP.getDimension());
        assertEquals(Dimension.COUNT, Unit.COUNT.getDimension());
    }

    @Test
    void parseAcceptsSymbolsAndNamesIgnoringCaseAndWhitespace() {
        assertEquals(Unit.GRAM, Unit.parse("g"));
        assertEquals(Unit.GRAM, Unit.parse("Grams"));
        assertEquals(Unit.KILOGRAM, Unit.parse(" KG "));
        assertEquals(Unit.CUP, Unit.parse("cups"));
        assertEquals(Unit.TABLESPOON, Unit.parse("tbsp"));
    }

    @Test
    void parseRejectsUnknownText() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Unit.parse("banana"));
        assertEquals("Unknown unit 'banana'. Use one of: g, kg, ml, l, tsp, tbsp, cup, pc", e.getMessage());
    }

    @Test
    void parseRejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> Unit.parse(null));
        assertThrows(IllegalArgumentException.class, () -> Unit.parse("   "));
    }
}
