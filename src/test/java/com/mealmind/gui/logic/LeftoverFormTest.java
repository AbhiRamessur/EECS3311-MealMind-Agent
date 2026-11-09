package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.leftovers.LeftoverRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeftoverFormTest {

    private static List<FieldError> errors(String days, String meals) {
        FormResult<LeftoverRequest> result = LeftoverForm.parse(days, meals, false);
        assertFalse(result.isValid());
        return result.errors();
    }

    @Test
    void validBoundsAtBothEnds() {
        LeftoverRequest low = LeftoverForm.parse("1", "1", false).value().orElseThrow();
        LeftoverRequest high = LeftoverForm.parse(" 14 ", "6", true).value().orElseThrow();

        assertEquals(new LeftoverRequest(1, 1, false), low);
        assertEquals(new LeftoverRequest(14, 6, true), high);
    }

    @Test
    void outOfRangeUsesTheRequestsOwnMessages() {
        String daysZero = assertThrows(IllegalArgumentException.class, () -> new LeftoverRequest(0, 1, false)).getMessage();
        String mealsSeven = assertThrows(IllegalArgumentException.class, () -> new LeftoverRequest(1, 7, false)).getMessage();

        assertEquals(List.of(new FieldError("days", daysZero)), errors("0", "3"));
        assertEquals(List.of(new FieldError("days", "Days must be from 1 to 14, got 15")), errors("15", "3"));
        assertEquals(List.of(new FieldError("meals", mealsSeven)), errors("3", "7"));
        assertEquals(List.of(new FieldError("meals", "Meals must be from 1 to 6, got 0")), errors("3", "0"));
    }

    @Test
    void textIsNotANumber() {
        assertEquals(List.of(new FieldError("days", "must be a number")), errors("three", "3"));
        assertEquals(List.of(new FieldError("meals", "must be a number")), errors("3", ""));
        assertEquals(List.of(new FieldError("days", "must be a number")), errors(null, "3"));
    }

    @Test
    void everyErrorIsCollected() {
        List<FieldError> both = errors("15", "x");

        assertEquals(List.of("days", "meals"), both.stream().map(FieldError::field).toList());
        assertTrue(errors("0", "7").size() == 2);
    }
}
