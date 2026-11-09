package com.mealmind.substitution;

import com.mealmind.cli.CliFormat;
import com.mealmind.model.Ingredient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The wording of F07 (ingredient substitution), shared by the CLI's {@code recipe substitute} and the GUI's
 * "Substitute an ingredient" section, so the two front ends cannot drift. Quantities use the CLI's
 * {@link CliFormat#number} rounding.
 */
public final class SubstituteText {

    /** The CLI hint after a suggestion. */
    public static final String APPLY_HINT =
            "Use 'recipe substitute apply <n>' to apply it ('undo' reverts) or 'recipe substitute discard'.";
    /** Shown after a successful apply. */
    public static final String UNDO_HINT = "Use 'undo' to revert.";
    /** Shown after a discard. */
    public static final String DISCARDED = "Discarded the pending substitution.";
    /** Shown when there is nothing to apply or discard. */
    public static final String NO_PENDING = "No pending substitution.";
    /** Marker for an option the pantry already holds. */
    public static final String IN_PANTRY = "in pantry";

    private SubstituteText() {
    }

    /**
     * The heading above the options.
     *
     * @param proposal the proposal
     * @return e.g. "Substitutes for Chicken breast in R101 Chicken Stir-Fry (reason: allergy):"
     */
    public static String header(SubstitutionProposal proposal) {
        return "Substitutes for " + proposal.target().getName() + " in " + proposal.recipe().getId() + " "
                + proposal.recipe().getName() + " (reason: " + proposal.reason().getDisplayName().toLowerCase(Locale.ROOT) + "):";
    }

    /**
     * The amount, name and ratio of one option.
     *
     * @param proposal the proposal
     * @param index    the 0-based option index
     * @return e.g. "450 g Chickpeas (x1.5)"
     */
    public static String optionText(SubstitutionProposal proposal, int index) {
        Ingredient target = proposal.target();
        SubstitutionOption option = proposal.options().get(index);
        return CliFormat.number(target.getQuantity() * option.ratio(), target.getUnit()) + " " + target.getUnit().getSymbol()
                + " " + option.name() + " (x" + ratio(option.ratio()) + ")";
    }

    /**
     * One numbered option line as the CLI prints it.
     *
     * @param proposal the proposal
     * @param index    the 0-based option index
     * @return e.g. "  1. 300 g Firm tofu (x1) - Absorbs the sauce [in pantry]"
     */
    public static String optionLine(SubstitutionProposal proposal, int index) {
        SubstitutionOption option = proposal.options().get(index);
        String note = option.note().isEmpty() ? "" : " - " + option.note();
        return "  " + (index + 1) + ". " + optionText(proposal, index) + note + (option.inPantry() ? " [" + IN_PANTRY + "]" : "");
    }

    /**
     * The estimated nutrition of one option.
     *
     * @param option the option
     * @return e.g. "about 450 kcal, P 48.0 g, C 6.0 g, F 27.0 g (AI estimates)"
     */
    public static String nutritionText(SubstitutionOption option) {
        return String.format(Locale.ROOT, "about %.0f kcal, P %.1f g, C %.1f g, F %.1f g (AI estimates)",
                option.calories(), option.proteinG(), option.carbsG(), option.fatG());
    }

    /**
     * The ratio with at most 2 decimals and no trailing zeros.
     *
     * @param value the ratio
     * @return e.g. "1.5", "2", "0.75"
     */
    public static String ratio(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * The proposal's notes as "Note: ..." lines.
     *
     * @param proposal the proposal
     * @return the lines
     */
    public static List<String> noteLines(SubstitutionProposal proposal) {
        return proposal.notes().stream().map(note -> "Note: " + note).toList();
    }

    /**
     * Everything the CLI prints for a proposal: the header, two lines per option, the notes and the hint.
     *
     * @param proposal the proposal
     * @return the lines
     */
    public static List<String> proposalLines(SubstitutionProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        List<String> lines = new ArrayList<>();
        lines.add(header(proposal));
        for (int i = 0; i < proposal.options().size(); i++) {
            lines.add(optionLine(proposal, i));
            lines.add("     " + nutritionText(proposal.options().get(i)));
        }
        lines.addAll(noteLines(proposal));
        lines.add(APPLY_HINT);
        return lines;
    }

    /**
     * The model-usage line printed after every request.
     *
     * @param result the result
     * @return e.g. "Agent: 1 model calls, 0 tool calls; tokens: 300 prompt + 120 completion"
     */
    public static String statsLine(SubstitutionResult result) {
        return "Agent: " + result.llmCalls() + " model calls, " + result.toolSteps() + " tool calls; tokens: "
                + result.usage().promptTokens() + " prompt + " + result.usage().completionTokens() + " completion";
    }

    /**
     * One applier warning as printed: the per-serving line as it is, every other one prefixed "Warning: ".
     *
     * @param warning the applier's warning
     * @return the line
     */
    public static String warningLine(String warning) {
        return warning.startsWith("Per serving:") ? warning : "Warning: " + warning;
    }

    /**
     * The note for a change to a bundled sample recipe.
     *
     * @param recipeId the recipe id
     * @return "Note: &lt;id&gt; is a bundled sample recipe; this change lasts for this session only."
     */
    public static String bundledNote(String recipeId) {
        return "Note: " + recipeId + " is a bundled sample recipe; this change lasts for this session only.";
    }

    /**
     * Everything the CLI prints after a successful apply.
     *
     * @param result   the applier's result
     * @param recipeId the recipe id
     * @param bundled  whether it is a bundled sample recipe
     * @return "Substituted: ...", the warning lines, the bundled note when applicable, and the undo hint
     */
    public static List<String> appliedLines(SubstitutionApplier.ApplyResult result, String recipeId, boolean bundled) {
        List<String> lines = new ArrayList<>();
        lines.add("Substituted: " + result.message());
        result.warnings().forEach(warning -> lines.add(warningLine(warning)));
        if (bundled) {
            lines.add(bundledNote(recipeId));
        }
        lines.add(UNDO_HINT);
        return lines;
    }
}
