package com.mealmind.cli;

import com.mealmind.app.ApplicationContext;
import com.mealmind.model.DayPlan;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.planning.PlanRequest;
import com.mealmind.planning.PlanResult;
import com.mealmind.planning.PlanningStrategies;
import com.mealmind.planning.PlanningStrategy;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The F05 rule-based planning commands: {@code plan strategies} and {@code plan generate}. No AI is used.
 *
 * <p>{@code plan generate} replaces the current plan through {@link ApplicationContext#newPlan}, which
 * clears the command history, so generating a whole plan is <strong>not undoable</strong>; edits made
 * afterwards with {@code plan set} or {@code plan clear} are.
 */
final class PlanGenerationCommands {

    static final String GENERATE_USAGE =
            "plan generate [--days <n>] [--strategy <id>] [--slots lunch,dinner] [--budget <n>] [--replace]";
    private static final String REPLACE = "--replace";

    private final ApplicationContext ctx;
    private final Runnable onNewPlan;
    private final PrintStream out;

    /**
     * @param ctx       the session
     * @param onNewPlan run after the generated meals are copied into the new current plan (the handler
     *                  moves its console view there, so the copy itself prints no update lines)
     * @param out       where output is printed
     */
    PlanGenerationCommands(ApplicationContext ctx, Runnable onNewPlan, PrintStream out) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.onNewPlan = Objects.requireNonNull(onNewPlan, "onNewPlan");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** {@code plan strategies}: one line per strategy with the first sentence of its guidance. */
    void strategies(List<String> args) {
        if (!args.isEmpty()) {
            throw new UsageException("plan strategies");
        }
        for (PlanningStrategy strategy : PlanningStrategies.all()) {
            out.println(String.format(Locale.ROOT, "%-13s %-17s %s",
                    strategy.id(), strategy.displayName(), firstSentence(strategy.promptGuidance())));
        }
    }

    /** {@code plan generate ...}: builds a plan with the rule-based planner and makes it the current plan. */
    void generate(List<String> args) {
        // "--replace" is a switch without a value, so it is taken out before CliArgs sees the other flags.
        List<String> rest = new ArrayList<>(args);
        boolean replace = rest.removeIf(token -> token.equalsIgnoreCase(REPLACE));
        if (rest.size() != args.size() - (replace ? 1 : 0)) {
            throw new UsageException(GENERATE_USAGE);
        }
        CliArgs parsed = CliArgs.parse(rest, Set.of("days", "strategy", "slots", "budget"), GENERATE_USAGE)
                .expectPositional(0, 0, GENERATE_USAGE);
        PlanRequest request = request(parsed);

        if (!replace && hasMeals(ctx.plan())) {
            out.println("Error: the current plan already has meals. Use --replace to overwrite it (this cannot be undone).");
            return;
        }
        // Generated first, so a failure leaves the current plan untouched.
        PlanResult result = ctx.planner().generate(request);
        MealPlan generated = result.plan();
        MealPlan current = ctx.newPlan(request.days());
        for (DayPlan day : generated.getDays()) {
            for (MealType type : day.getFilledSlots()) {
                current.setMeal(day.getDayNumber(), type, day.getMeal(type).orElseThrow());
            }
        }
        onNewPlan.run();

        out.println("Generated " + request.days() + "-day plan using " + request.strategy().displayName()
                + " (rule-based, no AI)");
        result.notes().forEach(out::println);
        result.validation().getAdvisories().forEach(v -> out.println("Warning: " + v.message()));
        result.validation().getSafetyViolations().forEach(v -> out.println("SAFETY: " + v.message()));
        out.println("Use 'plan show' or 'shopping list'.");
    }

    private static PlanRequest request(CliArgs parsed) {
        PlanRequest defaults = PlanRequest.defaults();
        int days = parsed.flags().containsKey("days")
                ? CliArgs.parseInt(parsed.flags().get("days"), GENERATE_USAGE) : defaults.days();
        PlanningStrategy strategy = parsed.flags().containsKey("strategy")
                ? PlanningStrategies.byId(parsed.flags().get("strategy")) : defaults.strategy();
        List<MealType> slots = parsed.flags().containsKey("slots")
                ? slots(parsed.flags().get("slots")) : defaults.slots();
        // Boxed explicitly: a plain ternary would unbox the null default budget.
        Double budget = parsed.flags().containsKey("budget")
                ? Double.valueOf(CliArgs.parseDouble(parsed.flags().get("budget"), GENERATE_USAGE)) : defaults.budget();
        return new PlanRequest(days, strategy, slots, budget);
    }

    /** Parses {@code "lunch,dinner"}; empty items, repeats and more than four slots are usage errors. */
    private static List<MealType> slots(String text) {
        String[] items = text.split(",", -1);
        if (items.length > PlanRequest.MAX_SLOTS) {
            throw new UsageException(GENERATE_USAGE);
        }
        List<MealType> slots = new ArrayList<>();
        for (String item : items) {
            if (item.isBlank()) {
                throw new UsageException(GENERATE_USAGE);
            }
            MealType type = MealType.parse(item);
            if (slots.contains(type)) {
                throw new UsageException(GENERATE_USAGE);
            }
            slots.add(type);
        }
        return slots;
    }

    private static boolean hasMeals(MealPlan plan) {
        return plan.getDays().stream().anyMatch(day -> !day.getFilledSlots().isEmpty());
    }

    /** The text up to and including the first ". ", or all of it. */
    static String firstSentence(String text) {
        String trimmed = text.trim();
        int end = trimmed.indexOf(". ");
        return end < 0 ? trimmed : trimmed.substring(0, end + 1);
    }
}
