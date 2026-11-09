package com.mealmind.cli;

import com.mealmind.agent.AgentEventType;
import com.mealmind.app.ApplicationContext;
import com.mealmind.leftovers.LeftoverAssignment;
import com.mealmind.leftovers.LeftoverFlow;
import com.mealmind.leftovers.LeftoverPlanner;
import com.mealmind.leftovers.LeftoverProposal;
import com.mealmind.leftovers.LeftoverRequest;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * {@code plan leftovers ...} (F10): proposes meals that use up expiring pantry items, then applies or
 * discards the proposal. A proposal changes nothing; {@code plan leftovers apply} adds all its meals as one
 * undoable step. With {@code --ai} the AI may only reorder the safe candidates the code chose; if that fails,
 * the deterministic proposal stands and one "AI explanation unavailable: ..." line is printed. The flow and its
 * wording live in {@link LeftoverFlow}, shared with the GUI; this class parses the arguments and prints.
 */
final class LeftoverCommands {

    static final String USAGE = "plan leftovers [--days 1-14] [--meals 1-6] [--overwrite] [--ai]";
    private static final String OVERWRITE = "--overwrite";
    private static final String AI = "--ai";

    private final ApplicationContext ctx;
    private final PrintStream out;
    private LeftoverProposal pending;

    LeftoverCommands(ApplicationContext ctx, PrintStream out) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** Routes {@code plan leftovers [apply|discard|options]}. */
    void handle(List<String> args) {
        if (args.size() == 1 && args.get(0).equalsIgnoreCase("apply")) {
            apply();
        } else if (args.size() == 1 && args.get(0).equalsIgnoreCase("discard")) {
            discard();
        } else {
            propose(args);
        }
    }

    /** Built from the context on every call, because plan new and plan generate replace the plan. */
    private LeftoverPlanner planner() {
        return new LeftoverPlanner(ctx.recipeBook(), ctx.pantry(), ctx.validator(), ctx.profile(), ctx.preferences());
    }

    private void propose(List<String> args) {
        LeftoverRequest request = parse(args);
        boolean useAi = args.stream().anyMatch(arg -> arg.equalsIgnoreCase(AI));
        LeftoverPlanner planner = planner();
        LeftoverFlow.Outcome outcome = LeftoverFlow.propose(planner, ctx.plan(), request, LocalDate.now(ctx.clock()));
        pending = null;
        if (outcome.blocked().isPresent()) {
            out.println(outcome.blocked().get());
            return;
        }
        if (useAi) {
            outcome = LeftoverFlow.rerank(planner, outcome.candidates().orElseThrow(), ctx.plan(), request, ctx.agent(),
                    event -> {
                        if (event.type() != AgentEventType.ERROR) {
                            out.println("[agent] " + event.message());
                        }
                    });
        }
        LeftoverProposal proposal = outcome.proposal().orElseThrow();
        print(proposal, request, outcome.notes());
        outcome.aiProblem().ifPresent(problem -> out.println(LeftoverFlow.AI_PROBLEM_PREFIX + problem));
        pending = proposal;
    }

    /** Reads the switches and flags; anything else is a usage error. */
    private static LeftoverRequest parse(List<String> args) {
        List<String> rest = new ArrayList<>(args);
        boolean overwrite = takeSwitch(rest, OVERWRITE);
        takeSwitch(rest, AI);
        CliArgs parsed = CliArgs.parse(rest, Set.of("days", "meals"), USAGE).expectPositional(0, 0, USAGE);
        LeftoverRequest defaults = LeftoverRequest.defaults();
        int days = parsed.flags().containsKey("days")
                ? CliArgs.parseInt(parsed.flags().get("days"), USAGE) : defaults.withinDays();
        int meals = parsed.flags().containsKey("meals")
                ? CliArgs.parseInt(parsed.flags().get("meals"), USAGE) : defaults.meals();
        try {
            return new LeftoverRequest(days, meals, overwrite);
        } catch (IllegalArgumentException e) {
            throw new UsageException(USAGE);
        }
    }

    /** Removes {@code name} (any case) from {@code tokens}; given twice is a usage error. */
    private static boolean takeSwitch(List<String> tokens, String name) {
        int before = tokens.size();
        tokens.removeIf(token -> token.toLowerCase(Locale.ROOT).equals(name));
        if (before - tokens.size() > 1) {
            throw new UsageException(USAGE);
        }
        return tokens.size() < before;
    }

    private void print(LeftoverProposal proposal, LeftoverRequest request, List<String> extraNotes) {
        LeftoverFlow.headerLines(proposal, request, extraNotes).forEach(out::println);
        out.println(LeftoverFlow.PROPOSAL_TITLE);
        for (LeftoverAssignment assignment : proposal.assignments()) {
            String replaces = assignment.replacesExisting()
                    ? proposal.plan().getMeal(assignment.day(), assignment.slot())
                            .map(meal -> " [replaces " + CLIHandler.describe(meal) + "]").orElse("")
                    : "";
            out.println("  Day " + assignment.day() + " " + assignment.slot() + ": " + assignment.recipe().getName()
                    + " - " + assignment.reason() + replaces);
        }
        out.println("Use 'plan leftovers apply' to add these meals ('undo' reverts them all) or 'plan leftovers discard'.");
    }

    private void apply() {
        if (pending == null) {
            out.println(LeftoverFlow.NO_PENDING);
            return;
        }
        List<String> lines;
        try {
            lines = planner().apply(pending, ctx.plan(), ctx.history());
        } catch (IllegalStateException e) {
            if (pending.plan() != ctx.plan()) {
                pending = null;
            }
            throw e;
        }
        pending = null;
        out.println(LeftoverFlow.appliedMessage(ctx.history().nextUndoDescription().orElseThrow()));
        lines.forEach(out::println);
        out.println(LeftoverFlow.UNDO_HINT);
    }

    private void discard() {
        if (pending == null) {
            out.println(LeftoverFlow.NO_PENDING);
            return;
        }
        pending = null;
        out.println(LeftoverFlow.DISCARDED);
    }
}
