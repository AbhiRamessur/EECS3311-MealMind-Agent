package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.substitution.SubstitutionOption;
import com.mealmind.substitution.SubstitutionProposal;
import com.mealmind.substitution.SubstitutionReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubstituteRowsTest {

    private static final Ingredient CHICKEN = new Ingredient("Chicken breast", 300, Unit.GRAM, 495, 93, 0, 11, 4.50);
    private static final Recipe RECIPE = new Recipe("T1", "Stir-Fry", 2, 20, List.of("Cook."), List.of(CHICKEN));
    private static final SubstitutionOption TOFU = new SubstitutionOption("Firm tofu", 1.0, "Absorbs the sauce", 450, 48, 6, 27, 2, true);
    private static final SubstitutionOption CHICKPEAS = new SubstitutionOption("Chickpeas", 1.5, "", 540, 30, 90, 9, 2, false);
    private static final SubstitutionOption BEANS = new SubstitutionOption("White beans", 0.5, "Softer", 300, 20, 50, 1, 1, false);

    private static List<SubstituteRow> rows(SubstitutionOption... options) {
        return SubstituteRows.of(new SubstitutionProposal(RECIPE, 0, CHICKEN, SubstitutionReason.MISSING, List.of(options), List.of()));
    }

    @Test
    void oneRow() {
        assertEquals(List.of(new SubstituteRow("1", "300 g Firm tofu (x1)", "Absorbs the sauce", "in pantry",
                "about 450 kcal, P 48.0 g, C 6.0 g, F 27.0 g (AI estimates)")), rows(TOFU));
    }

    @Test
    void threeRowsInOrderWithEmptyNoteAndNoPantryFlag() {
        List<SubstituteRow> rows = rows(TOFU, CHICKPEAS, BEANS);

        assertEquals(List.of("1", "2", "3"), rows.stream().map(SubstituteRow::numberText).toList());
        assertEquals(new SubstituteRow("2", "450 g Chickpeas (x1.5)", "", "",
                "about 540 kcal, P 30.0 g, C 90.0 g, F 9.0 g (AI estimates)"), rows.get(1));
        assertEquals("150 g White beans (x0.5)", rows.get(2).optionText());
    }

    @Test
    void twoRows() {
        List<SubstituteRow> rows = rows(CHICKPEAS, BEANS);

        assertEquals(List.of("450 g Chickpeas (x1.5)", "150 g White beans (x0.5)"), rows.stream().map(SubstituteRow::optionText).toList());
        assertEquals(List.of("", "Softer"), rows.stream().map(SubstituteRow::noteText).toList());
    }
}
