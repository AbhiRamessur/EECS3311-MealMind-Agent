package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.substitution.SubstitutionOption;
import com.mealmind.substitution.SubstitutionProposal;
import com.mealmind.substitution.SubstitutionReason;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubstituteSessionTest {

    private static final Ingredient RICE = new Ingredient("Rice", 100, Unit.GRAM, 100, 2, 20, 1, 0.50);
    private static final Recipe RECIPE = new Recipe("T1", "Bowl", 1, 5, List.of("Mix."), List.of(RICE));

    private static SubstitutionProposal proposal(String optionName) {
        return new SubstitutionProposal(RECIPE, 0, RICE, SubstitutionReason.MISSING,
                List.of(new SubstitutionOption(optionName, 1, "", 100, 2, 20, 1, 0.5, false)), List.of());
    }

    @Test
    void holdsOneProposalAtATime() {
        SubstituteSession session = new SubstituteSession();
        assertFalse(session.isPending());
        assertEquals(Optional.empty(), session.proposal());

        SubstitutionProposal second = proposal("Quinoa");
        session.set(proposal("Couscous"));
        session.set(second);

        assertTrue(session.isPending());
        assertSame(second, session.proposal().orElseThrow());
        session.clear();
        assertFalse(session.isPending());
        assertThrows(NullPointerException.class, () -> session.set(null));
    }

    @Test
    void staleMeansTheBookHoldsAnotherInstance() {
        RecipeBook book = new RecipeBook();
        book.put(RECIPE);
        SubstituteSession session = new SubstituteSession();
        assertFalse(session.isStaleIn(book));

        session.set(proposal("Quinoa"));
        assertFalse(session.isStaleIn(book));

        book.put(new Recipe("T1", "Bowl", 1, 5, List.of("Mix."), List.of(RICE)));
        assertTrue(session.isStaleIn(book));
        book.remove("T1");
        assertTrue(session.isStaleIn(book));
    }
}
