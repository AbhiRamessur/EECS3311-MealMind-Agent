package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.leftovers.LeftoverProposal;
import com.mealmind.leftovers.LeftoverRequest;
import com.mealmind.model.MealPlan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LeftoverSessionTest {

    @Test
    void holdsOneProposalAtATime() {
        LeftoverSession session = new LeftoverSession();
        MealPlan plan = new MealPlan("Weekly Plan", 2);
        LeftoverProposal first = new LeftoverProposal(plan, List.of(), List.of(), List.of("first"));
        LeftoverProposal second = new LeftoverProposal(plan, List.of(), List.of(), List.of("second"));
        LeftoverRequest request = LeftoverRequest.defaults();

        assertFalse(session.isPending());
        assertEquals(Optional.empty(), session.proposal());
        session.set(first, request);
        session.set(second, request);

        assertTrue(session.isPending());
        assertSame(second, session.proposal().orElseThrow());
        assertSame(request, session.request().orElseThrow());
        session.clear();
        assertFalse(session.isPending());
        assertEquals(Optional.empty(), session.request());
        assertThrows(NullPointerException.class, () -> session.set(null, request));
    }

    @Test
    void staleMeansMadeForAnotherPlanObject() {
        LeftoverSession session = new LeftoverSession();
        MealPlan plan = new MealPlan("Weekly Plan", 2);
        assertFalse(session.isStaleFor(plan));

        session.set(new LeftoverProposal(plan, List.of(), List.of(), List.of()), LeftoverRequest.defaults());

        assertFalse(session.isStaleFor(plan));
        assertTrue(session.isStaleFor(new MealPlan("Weekly Plan", 2)));
    }
}
