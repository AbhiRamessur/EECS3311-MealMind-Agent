package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PantryFormTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 11, 2);

    @Test
    void validAddWithAndWithoutExpiry() {
        assertEquals(new PantryItem("Rice", 500, Unit.GRAM, EXPIRY), PantryForm.parse("Rice", " 500 ", Unit.GRAM, EXPIRY).value().orElseThrow());
        PantryItem milk = PantryForm.parse("Milk", "1.5", Unit.LITER, null).value().orElseThrow();
        assertEquals(1.5, milk.getQuantity());
        assertEquals(Optional.empty(), milk.getExpiry());
    }

    @Test
    void blankNameUsesThePantryItemMessage() {
        String expected = assertThrows(IllegalArgumentException.class, () -> new PantryItem(" ", 1, Unit.GRAM)).getMessage();

        assertEquals(List.of(new FieldError("name", expected)), PantryForm.parse("  ", "1", Unit.GRAM, null).errors());
    }

    @Test
    void badQuantities() {
        String zero = assertThrows(IllegalArgumentException.class, () -> new PantryItem("Rice", 0, Unit.GRAM)).getMessage();
        String negative = assertThrows(IllegalArgumentException.class, () -> new PantryItem("Rice", -2, Unit.GRAM)).getMessage();

        assertEquals(List.of(new FieldError("quantity", zero)), PantryForm.parse("Rice", "0", Unit.GRAM, null).errors());
        assertEquals(List.of(new FieldError("quantity", negative)), PantryForm.parse("Rice", "-2", Unit.GRAM, null).errors());
        for (String text : new String[] {"lots", "NaN", "", "1,5", "Infinity"}) {
            assertEquals(List.of(new FieldError("quantity", "must be a number")), PantryForm.parse("Rice", text, Unit.GRAM, null).errors(), text);
        }
    }

    @Test
    void nullUnitAndSeveralErrorsAtOnce() {
        String unit = assertThrows(IllegalArgumentException.class, () -> new PantryItem("Rice", 1, null)).getMessage();
        FormResult<PantryItem> result = PantryForm.parse("", "abc", null, EXPIRY);

        assertFalse(result.isValid());
        assertEquals(List.of("name", "quantity", "unit"), result.errors().stream().map(FieldError::field).toList());
        assertEquals(unit, result.errors().get(2).message());
        assertTrue(result.value().isEmpty());
    }
}
