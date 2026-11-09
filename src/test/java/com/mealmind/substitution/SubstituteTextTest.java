package com.mealmind.substitution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.llm.TokenUsage;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubstituteTextTest {

    private static final Ingredient CHICKEN = new Ingredient("Chicken breast", 300, Unit.GRAM, 495, 93, 0, 11, 4.50);
    private static final Recipe RECIPE = new Recipe("T1", "Stir-Fry", 2, 20, List.of("Cook."),
            List.of(CHICKEN, new Ingredient("Rice", 180, Unit.GRAM, 650, 13, 140, 1.5, 0.90)));
    private static final SubstitutionOption TOFU = new SubstitutionOption("Firm tofu", 1.0, "Absorbs the sauce", 450, 48, 6, 27, 2, true);
    private static final SubstitutionOption CHICKPEAS = new SubstitutionOption("Chickpeas", 1.5, "", 540, 30, 90, 9, 2, false);
    private static final SubstitutionOption BEANS = new SubstitutionOption("White beans", 0.75, "Softer", 300.4, 20.25, 50, 1, 1, false);

    private static SubstitutionProposal proposal(List<String> notes, SubstitutionOption... options) {
        return new SubstitutionProposal(RECIPE, 0, CHICKEN, SubstitutionReason.ALLERGY, List.of(options), notes);
    }

    @Test
    void headerNamesIngredientRecipeAndReason() {
        assertEquals("Substitutes for Chicken breast in T1 Stir-Fry (reason: allergy):", SubstituteText.header(proposal(List.of(), TOFU)));
    }

    @Test
    void optionLinesWithNotePantryFlagAndRatio() {
        SubstitutionProposal proposal = proposal(List.of(), TOFU, CHICKPEAS, BEANS);

        assertEquals("300 g Firm tofu (x1)", SubstituteText.optionText(proposal, 0));
        assertEquals("  1. 300 g Firm tofu (x1) - Absorbs the sauce [in pantry]", SubstituteText.optionLine(proposal, 0));
        assertEquals("  2. 450 g Chickpeas (x1.5)", SubstituteText.optionLine(proposal, 1));
        assertEquals("  3. 225 g White beans (x0.75) - Softer", SubstituteText.optionLine(proposal, 2));
    }

    @Test
    void ratioHasNoTrailingZerosAndAtMostTwoDecimals() {
        assertEquals("2", SubstituteText.ratio(2.0));
        assertEquals("1.5", SubstituteText.ratio(1.5));
        assertEquals("0.33", SubstituteText.ratio(0.333));
    }

    @Test
    void nutritionHasWholeCaloriesAndOneDecimalMacros() {
        assertEquals("about 450 kcal, P 48.0 g, C 6.0 g, F 27.0 g (AI estimates)", SubstituteText.nutritionText(TOFU));
        assertEquals("about 300 kcal, P 20.3 g, C 50.0 g, F 1.0 g (AI estimates)", SubstituteText.nutritionText(BEANS));
    }

    @Test
    void proposalLinesAndNotes() {
        SubstitutionProposal proposal = proposal(List.of("1 suggestion(s) were removed by the safety check."), TOFU);

        assertEquals(List.of("Note: 1 suggestion(s) were removed by the safety check."), SubstituteText.noteLines(proposal));
        assertEquals(List.of("Substitutes for Chicken breast in T1 Stir-Fry (reason: allergy):",
                "  1. 300 g Firm tofu (x1) - Absorbs the sauce [in pantry]",
                "     about 450 kcal, P 48.0 g, C 6.0 g, F 27.0 g (AI estimates)",
                "Note: 1 suggestion(s) were removed by the safety check.",
                "Use 'recipe substitute apply <n>' to apply it ('undo' reverts) or 'recipe substitute discard'."),
                SubstituteText.proposalLines(proposal));
    }

    @Test
    void statsLine() {
        SubstitutionResult result = new SubstitutionResult(Optional.empty(), "No safe substitute", 2, 1, new TokenUsage(300, 120));
        assertEquals("Agent: 2 model calls, 1 tool calls; tokens: 300 prompt + 120 completion", SubstituteText.statsLine(result));
    }

    @Test
    void appliedLinesWarningsBundledNoteAndHint() {
        SubstitutionApplier.ApplyResult result = new SubstitutionApplier.ApplyResult("Substituted Chicken breast with Firm tofu in Stir-Fry",
                RECIPE, List.of("Per serving: 707 -> 685 kcal", "Step 2 may still mention Chicken breast; steps are not rewritten"));

        assertEquals("Per serving: 707 -> 685 kcal", SubstituteText.warningLine("Per serving: 707 -> 685 kcal"));
        assertEquals("Warning: x", SubstituteText.warningLine("x"));
        assertEquals("Note: R101 is a bundled sample recipe; this change lasts for this session only.", SubstituteText.bundledNote("R101"));
        assertEquals(List.of("Substituted: Substituted Chicken breast with Firm tofu in Stir-Fry", "Per serving: 707 -> 685 kcal",
                "Warning: Step 2 may still mention Chicken breast; steps are not rewritten",
                "Note: T1 is a bundled sample recipe; this change lasts for this session only.", "Use 'undo' to revert."),
                SubstituteText.appliedLines(result, "T1", true));
        assertEquals(List.of("Substituted: Substituted Chicken breast with Firm tofu in Stir-Fry", "Per serving: 707 -> 685 kcal",
                "Warning: Step 2 may still mention Chicken breast; steps are not rewritten", "Use 'undo' to revert."),
                SubstituteText.appliedLines(result, "T1", false));
        assertEquals("Discarded the pending substitution.", SubstituteText.DISCARDED);
        assertEquals("No pending substitution.", SubstituteText.NO_PENDING);
    }
}
