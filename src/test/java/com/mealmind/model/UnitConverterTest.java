package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitConverterTest {

    @Test
    void convertsKilogramsAndGrams() {
        assertEquals(1000.0, UnitConverter.convert(1, Unit.KILOGRAM, Unit.GRAM));
        assertEquals(1.0, UnitConverter.convert(1000, Unit.GRAM, Unit.KILOGRAM));
    }

    @Test
    void convertsLitersToMilliliters() {
        assertEquals(1000.0, UnitConverter.convert(1, Unit.LITER, Unit.MILLILITER));
    }

    @Test
    void convertsCupsToMilliliters() {
        assertEquals(473.176, UnitConverter.convert(2, Unit.CUP, Unit.MILLILITER), 0.01);
    }

    @Test
    void convertsTeaspoonsToTablespoons() {
        assertEquals(1.0, UnitConverter.convert(3, Unit.TEASPOON, Unit.TABLESPOON), 0.001);
    }

    @Test
    void sameUnitConversionReturnsValueUnchanged() {
        assertEquals(123.456, UnitConverter.convert(123.456, Unit.CUP, Unit.CUP));
    }

    @Test
    void rejectsConversionAcrossDimensions() {
        assertThrows(IncompatibleUnitException.class, () -> UnitConverter.convert(1, Unit.GRAM, Unit.LITER));
        assertThrows(IncompatibleUnitException.class, () -> UnitConverter.convert(1, Unit.COUNT, Unit.GRAM));
        assertThrows(IncompatibleUnitException.class, () -> UnitConverter.convert(1, Unit.MILLILITER, Unit.COUNT));
    }

    @Test
    void incompatibleUnitMessageNamesBothUnits() {
        IncompatibleUnitException e = assertThrows(IncompatibleUnitException.class,
                () -> UnitConverter.convert(1, Unit.GRAM, Unit.LITER));
        assertTrue(e.getMessage().contains("GRAM"));
        assertTrue(e.getMessage().contains("LITER"));
    }

    @Test
    void rejectsNegativeNanAndInfiniteQuantities() {
        assertThrows(IllegalArgumentException.class, () -> UnitConverter.convert(-1, Unit.GRAM, Unit.KILOGRAM));
        assertThrows(IllegalArgumentException.class, () -> UnitConverter.convert(Double.NaN, Unit.GRAM, Unit.KILOGRAM));
        assertThrows(IllegalArgumentException.class,
                () -> UnitConverter.convert(Double.POSITIVE_INFINITY, Unit.GRAM, Unit.KILOGRAM));
    }

    @Test
    void gramKilogramRoundTripIsStable() {
        double kg = UnitConverter.convert(1234.5678, Unit.GRAM, Unit.KILOGRAM);
        assertEquals(1234.5678, UnitConverter.convert(kg, Unit.KILOGRAM, Unit.GRAM), 1e-9);
    }

    @Test
    void reportsCompatibility() {
        assertTrue(UnitConverter.areCompatible(Unit.GRAM, Unit.KILOGRAM));
        assertTrue(UnitConverter.areCompatible(Unit.CUP, Unit.TEASPOON));
        assertFalse(UnitConverter.areCompatible(Unit.GRAM, Unit.MILLILITER));
        assertFalse(UnitConverter.areCompatible(Unit.COUNT, Unit.LITER));
    }

    @Test
    void toBaseConvertsToGrams() {
        assertEquals(2000.0, UnitConverter.toBase(2, Unit.KILOGRAM));
    }
}
