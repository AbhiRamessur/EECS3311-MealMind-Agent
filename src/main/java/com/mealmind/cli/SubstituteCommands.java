package com.mealmind.cli;

import com.mealmind.agent.AgentEventType;
import com.mealmind.app.ApplicationContext;
import com.mealmind.model.Ingredient;
import com.mealmind.substitution.SubstitutionApplier;
import com.mealmind.substitution.SubstitutionOption;
import com.mealmind.substitution.SubstitutionProposal;
import com.mealmind.substitution.SubstitutionReason;
import com.mealmind.substitution.SubstitutionRequest;
import com.mealmind.substitution.SubstitutionResult;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * {@code recipe substitute ...} (F07): asks the AI for up to three safe substitutes for one ingredient, then
 * applies or discards the suggestion. A suggestion changes nothing; {@code recipe substitute apply <n>}
 * re-checks the option's safety and replaces the ingredient through an undoable command.
 */
final class SubstituteCommands {

    static final String USAGE = "recipe substitute --id <id> --ingredient \"<name>\" [--reason allergy|missing|preference]";
    static final String APPLY_USAGE = "recipe substitute apply <n> | recipe substitute discard";

    private final ApplicationContext ctx;
    private final PrintStream out;
    private SubstitutionProposal pending;

    SubstituteCommands(ApplicationContext ctx, PrintStream out) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** Routes {@code recipe substitute [apply n|discard|options]}. */
    void handle(List<String> args) {
        String first = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
        if (first.equals("apply")) {
            apply(args.subList(1, args.size()));
        } else if (first.equals("discard")) {
            if (args.size() != 1) {
                throw new UsageException(APPLY_USAGE);
            }
            discard();
        } else {
            suggest(args);
        }
    }

    private void suggest(List<String> args) {
        CliArgs parsed = CliArgs.parse(args, Set.of("id", "ingredient", "reason"), USAGE).expectPositional(0, 0, USAGE);
        String id = parsed.requireFlag("id", USAGE);
        String ingredient = parsed.requireFlag("ingredient", USAGE);
        SubstitutionReason reason = parsed.flags().containsKey("reason")
                ? SubstitutionReason.parse(parsed.flags().get("reason")) : SubstitutionReason.PREFERENCE;
        SubstitutionRequest request = new SubstitutionRequest(id, ingredient, reason);

        SubstitutionResult result = ctx.agent().suggestSubstitutions(request, ctx.pantry(), event -> {
            if (event.type() != AgentEventType.ERROR) {
                out.println("[agent] " + event.message());
            }
        });
        pending = result.proposal().orElse(null);
        if (pending == null) {
            out.println(result.message());
        } else {
            print(pending);
        }
        out.println("Agent: " + result.llmCalls() + " model calls, " + result.toolSteps() + " tool calls; tokens: "
                + result.usage().promptTokens() + " prompt + " + result.usage().completionTokens() + " completion");
    }

    private void print(SubstitutionProposal proposal) {
        Ingredient target = proposal.target();
        out.println("Substitutes for " + target.getName() + " in " + proposal.recipe().getId() + " "
                + proposal.recipe().getName() + " (reason: " + proposal.reason().getDisplayName().toLowerCase(Locale.ROOT) + "):");
        List<SubstitutionOption> options = proposal.options();
        for (int i = 0; i < options.size(); i++) {
            SubstitutionOption option = options.get(i);
            String note = option.note().isEmpty() ? "" : " - " + option.note();
            out.println("  " + (i + 1) + ". " + CliFormat.number(target.getQuantity() * option.ratio(), target.getUnit())
                    + " " + target.getUnit().getSymbol() + " " + option.name() + " (x" + ratio(option.ratio()) + ")"
                    + note + (option.inPantry() ? " [in pantry]" : ""));
            out.println(String.format(Locale.ROOT, "     about %.0f kcal, P %.1f g, C %.1f g, F %.1f g (AI estimates)",
                    option.calories(), option.proteinG(), option.carbsG(), option.fatG()));
        }
        proposal.notes().forEach(note -> out.println("Note: " + note));
        out.println("Use 'recipe substitute apply <n>' to apply it ('undo' reverts) or 'recipe substitute discard'.");
    }

    /** At most 2 decimals, without trailing zeros: 1.5, 2, 0.75. */
    private static String ratio(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private void apply(List<String> args) {
        if (args.size() != 1) {
            throw new UsageException(APPLY_USAGE);
        }
        if (pending == null) {
            out.println("No pending substitution.");
            return;
        }
        int number = CliArgs.parseInt(args.get(0), APPLY_USAGE);
        String id = pending.recipe().getId();
        SubstitutionApplier.ApplyResult result;
        try {
            result = new SubstitutionApplier(ctx.recipeBook(), ctx.validator()).apply(pending, number, ctx.history());
        } catch (IllegalStateException e) {
            if (ctx.recipeBook().get(id).orElse(null) != pending.recipe()) {
                pending = null;
            }
            throw e;
        }
        pending = null;
        out.println("Substituted: " + result.message());
        for (String warning : result.warnings()) {
            out.println(warning.startsWith("Per serving:") ? warning : "Warning: " + warning);
        }
        if (ctx.isBundledRecipe(id)) {
            out.println("Note: " + id + " is a bundled sample recipe; this change lasts for this session only.");
        }
        out.println("Use 'undo' to revert.");
    }

    private void discard() {
        if (pending == null) {
            out.println("No pending substitution.");
            return;
        }
        pending = null;
        out.println("Discarded the pending substitution.");
    }
}
