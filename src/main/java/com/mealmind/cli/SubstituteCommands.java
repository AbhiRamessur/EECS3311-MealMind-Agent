package com.mealmind.cli;

import com.mealmind.agent.AgentEventType;
import com.mealmind.app.ApplicationContext;
import com.mealmind.substitution.SubstituteText;
import com.mealmind.substitution.SubstitutionApplier;
import com.mealmind.substitution.SubstitutionProposal;
import com.mealmind.substitution.SubstitutionReason;
import com.mealmind.substitution.SubstitutionRequest;
import com.mealmind.substitution.SubstitutionResult;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * {@code recipe substitute ...} (F07): asks the AI for up to three safe substitutes for one ingredient, then
 * applies or discards the suggestion. A suggestion changes nothing; {@code recipe substitute apply <n>}
 * re-checks the option's safety and replaces the ingredient through an undoable command. The wording lives in
 * {@link SubstituteText}, shared with the GUI; this class parses the arguments and prints.
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
            SubstituteText.proposalLines(pending).forEach(out::println);
        }
        out.println(SubstituteText.statsLine(result));
    }

    private void apply(List<String> args) {
        if (args.size() != 1) {
            throw new UsageException(APPLY_USAGE);
        }
        if (pending == null) {
            out.println(SubstituteText.NO_PENDING);
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
        SubstituteText.appliedLines(result, id, ctx.isBundledRecipe(id)).forEach(out::println);
    }

    private void discard() {
        if (pending == null) {
            out.println(SubstituteText.NO_PENDING);
            return;
        }
        pending = null;
        out.println(SubstituteText.DISCARDED);
    }
}
