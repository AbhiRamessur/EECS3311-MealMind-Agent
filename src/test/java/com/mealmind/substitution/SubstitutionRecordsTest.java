package com.mealmind.substitution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.llm.TokenUsage;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** SubstitutionReason, SubstitutionRequest, SubstitutionOption, SubstitutionProposal and SubstitutionResult. */
class SubstitutionRecordsTest {

    private static final Ingredient BUTTER = new Ingredient("Peanut butter", 100, Unit.GRAM, 600, 24, 20, 50, 2.00);
    private static final Recipe RECIPE = new Recipe("T1", "Peanut Noodles", 2, 15, List.of("Cook."),
            List.of(new Ingredient("Noodles", 200, Unit.GRAM, 700, 24, 140, 4, 1.00), BUTTER));

    private static SubstitutionOption option(String name, double ratio) {
        return new SubstitutionOption(name, ratio, "", 100, 5, 10, 4, 1.00, false);
    }

    @Test
    void reasonParsesIgnoringCaseAndSpaces() {
        assertEquals(SubstitutionReason.ALLERGY, SubstitutionReason.parse(" Allergy "));
        assertEquals(SubstitutionReason.MISSING, SubstitutionReason.parse("MISSING"));
        assertEquals(SubstitutionReason.PREFERENCE, SubstitutionReason.parse("preference"));
        assertEquals("Allergy", SubstitutionReason.ALLERGY.getDisplayName());
        assertEquals("Unknown reason 'banana'. Use: allergy, missing, preference",
                assertThrows(IllegalArgumentException.class, () -> SubstitutionReason.parse("banana")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> SubstitutionReason.parse(null));
    }

    @Test
    void requestTrimsAndRejectsBlanks() {
        SubstitutionRequest request = new SubstitutionRequest(" R101 ", " chicken ", SubstitutionReason.MISSING);
        assertEquals("R101", request.recipeId());
        assertEquals("chicken", request.ingredientName());
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionRequest(" ", "x", SubstitutionReason.MISSING));
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionRequest("R1", "", SubstitutionReason.MISSING));
        assertThrows(NullPointerException.class, () -> new SubstitutionRequest("R1", "x", null));
    }

    @Test
    void optionRatioBoundsAreInclusive() {
        assertEquals(0.25, option("Tofu", 0.25).ratio());
        assertEquals(4.0, option("Tofu", 4.0).ratio());
        assertThrows(IllegalArgumentException.class, () -> option("Tofu", 0.24));
        assertThrows(IllegalArgumentException.class, () -> option("Tofu", 4.01));
        assertThrows(IllegalArgumentException.class, () -> option("Tofu", Double.NaN));
    }

    @Test
    void optionNameNoteAndAmountBounds() {
        assertEquals("Tofu", option("  Tofu  ", 1).name());
        assertEquals(60, option("x".repeat(60), 1).name().length());
        assertThrows(IllegalArgumentException.class, () -> option(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> option("x".repeat(61), 1));
        assertEquals(140, new SubstitutionOption("Tofu", 1, "n".repeat(140), 0, 0, 0, 0, 0, true).note().length());
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionOption("Tofu", 1, "n".repeat(141), 0, 0, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionOption("Tofu", 1, "", -1, 0, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionOption("Tofu", 1, "", 0, -1, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionOption("Tofu", 1, "", 0, 0, -1, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionOption("Tofu", 1, "", 0, 0, 0, -1, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionOption("Tofu", 1, "", 0, 0, 0, 0, -1, true));
        assertThrows(IllegalArgumentException.class,
                () -> new SubstitutionOption("Tofu", 1, "", Double.POSITIVE_INFINITY, 0, 0, 0, 0, true));
    }

    @Test
    void proposalNeedsOneToThreeOptionsAndAValidIndex() {
        List<SubstitutionOption> three = List.of(option("A", 1), option("B", 1), option("C", 1));
        assertEquals(3, new SubstitutionProposal(RECIPE, 1, BUTTER, SubstitutionReason.ALLERGY, three, List.of()).options().size());
        assertThrows(IllegalArgumentException.class,
                () -> new SubstitutionProposal(RECIPE, 1, BUTTER, SubstitutionReason.ALLERGY, List.of(), List.of()));
        List<SubstitutionOption> four = List.of(option("A", 1), option("B", 1), option("C", 1), option("D", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SubstitutionProposal(RECIPE, 1, BUTTER, SubstitutionReason.ALLERGY, four, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SubstitutionProposal(RECIPE, 2, BUTTER, SubstitutionReason.ALLERGY, three, List.of()));
    }

    @Test
    void listsAreCopiedAndUnmodifiable() {
        List<SubstitutionOption> options = new ArrayList<>(List.of(option("A", 1)));
        List<String> notes = new ArrayList<>(List.of("note"));
        SubstitutionProposal proposal = new SubstitutionProposal(RECIPE, 1, BUTTER, SubstitutionReason.MISSING, options, notes);
        options.add(option("B", 1));
        notes.clear();

        assertEquals(1, proposal.options().size());
        assertEquals(List.of("note"), proposal.notes());
        assertThrows(UnsupportedOperationException.class, () -> proposal.options().add(option("C", 1)));
        assertThrows(UnsupportedOperationException.class, () -> proposal.notes().add("x"));
    }

    @Test
    void resultNeedsAMessage() {
        SubstitutionResult result = new SubstitutionResult(Optional.empty(), "No safe substitute", 1, 0, TokenUsage.ZERO);
        assertEquals("No safe substitute", result.message());
        assertThrows(IllegalArgumentException.class,
                () -> new SubstitutionResult(Optional.empty(), " ", 1, 0, TokenUsage.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SubstitutionResult(Optional.empty(), "x", -1, 0, TokenUsage.ZERO));
    }
}
