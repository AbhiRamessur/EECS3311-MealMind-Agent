package com.mealmind.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.validation.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanRequestTest {

    private static final PlanningStrategy BUDGET = new BudgetConsciousStrategy();
    private static final List<MealType> DINNER = List.of(MealType.DINNER);

    @Test
    void defaults() {
        PlanRequest request = PlanRequest.defaults();

        assertEquals(7, request.days());
        assertEquals("pantry-first", request.strategy().id());
        assertEquals(List.of(MealType.LUNCH, MealType.DINNER), request.slots());
        assertNull(request.budget());
    }

    @Test
    void daysMustBeOneToSeven() {
        assertEquals(1, new PlanRequest(1, BUDGET, DINNER, null).days());
        assertEquals(7, new PlanRequest(7, BUDGET, DINNER, null).days());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new PlanRequest(8, BUDGET, DINNER, null));
        assertEquals("Days must be between 1 and 7, got 8", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new PlanRequest(0, BUDGET, DINNER, null));
    }

    @Test
    void slotsAreCopiedInOrderAndMustBeDistinct() {
        List<MealType> slots = new ArrayList<>(List.of(MealType.SNACK, MealType.BREAKFAST));
        PlanRequest request = new PlanRequest(2, BUDGET, slots, null);
        slots.clear();

        assertEquals(List.of(MealType.SNACK, MealType.BREAKFAST), request.slots());
        assertThrows(UnsupportedOperationException.class, () -> request.slots().add(MealType.LUNCH));
        assertThrows(IllegalArgumentException.class, () -> new PlanRequest(2, BUDGET, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanRequest(2, BUDGET, List.of(MealType.LUNCH, MealType.LUNCH), null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanRequest(2, BUDGET, Arrays.asList(MealType.LUNCH, null), null));
        assertThrows(IllegalArgumentException.class, () -> new PlanRequest(2, BUDGET, List.of(MealType.BREAKFAST,
                MealType.LUNCH, MealType.DINNER, MealType.SNACK, MealType.LUNCH), null));
    }

    @Test
    void budgetMustBeFiniteAndPositive() {
        assertEquals(50.0, new PlanRequest(1, BUDGET, DINNER, 50.0).budget());
        for (double bad : new double[] {0, -5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new PlanRequest(1, BUDGET, DINNER, bad), "" + bad);
        }
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> new PlanRequest(1, null, DINNER, null));
        assertThrows(NullPointerException.class, () -> new PlanRequest(1, BUDGET, null, null));
    }

    @Test
    void planResultCopiesNotes() {
        List<String> notes = new ArrayList<>(List.of("a"));
        PlanResult result = new PlanResult(new MealPlan("P", 1), ValidationResult.ok(), notes);
        notes.add("b");

        assertEquals(List.of("a"), result.notes());
        assertThrows(UnsupportedOperationException.class, () -> result.notes().add("c"));
        assertThrows(NullPointerException.class, () -> new PlanResult(null, ValidationResult.ok(), List.of()));
        assertThrows(NullPointerException.class, () -> new PlanResult(new MealPlan("P", 1), null, List.of()));
    }
}
