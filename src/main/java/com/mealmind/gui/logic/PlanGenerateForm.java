package com.mealmind.gui.logic;

import com.mealmind.model.MealType;
import com.mealmind.planning.PlanRequest;
import com.mealmind.planning.PlanningStrategies;
import com.mealmind.planning.PlanningStrategy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses the Plan tab's generate bar into a {@link PlanRequest}. Range and strategy messages come from
 * {@code PlanRequest} and {@link PlanningStrategies} themselves; every error is collected.
 */
public final class PlanGenerateForm {

    /** Shown when no slot or more than four are chosen. */
    public static final String SLOTS_MESSAGE = "choose between 1 and 4 meal slots";
    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d{1,9}");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d{1,12}(\\.\\d*)?|\\.\\d+)");

    private PlanGenerateForm() {
    }

    /**
     * Returns the strategy choices, in {@link PlanningStrategies#all()} order.
     *
     * @return the options
     */
    public static List<StrategyOption> strategyOptions() {
        return PlanningStrategies.all().stream().map(s -> new StrategyOption(s.id(), s.displayName())).toList();
    }

    /**
     * Parses the generate bar.
     *
     * @param days       a whole number from 1 to 7
     * @param strategyId a strategy id
     * @param slots      1 to 4 meal slots (kept in {@link MealType} order)
     * @param budget     a positive number, or blank for none
     * @return the request, or errors for "days", "strategy", "slots" and "budget"
     */
    public static FormResult<PlanRequest> parse(String days, String strategyId, Set<MealType> slots, String budget) {
        List<FieldError> errors = new ArrayList<>();
        PlanRequest defaults = PlanRequest.defaults();
        FormResult<Integer> parsedDays = parseDays(days);
        Integer dayCount = parsedDays.value().orElse(null);
        errors.addAll(parsedDays.errors());
        PlanningStrategy strategy = null;
        try {
            strategy = PlanningStrategies.byId(strategyId);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError("strategy", e.getMessage()));
        }
        List<MealType> chosen = slots == null ? List.of()
                : Arrays.stream(MealType.values()).filter(slots::contains).toList();
        if (chosen.isEmpty() || chosen.size() > PlanRequest.MAX_SLOTS) {
            errors.add(new FieldError("slots", SLOTS_MESSAGE));
        }
        Double amount = null;
        if (budget != null && !budget.isBlank()) {
            String budgetText = budget.trim();
            if (!DECIMAL.matcher(budgetText).matches()) {
                errors.add(new FieldError("budget", ProfileForm.NOT_A_NUMBER));
            } else {
                amount = Double.valueOf(budgetText);
                Double value = amount;
                check("budget", () -> new PlanRequest(1, defaults.strategy(), defaults.slots(), value), errors);
            }
        }
        if (!errors.isEmpty()) {
            return FormResult.failed(errors);
        }
        return FormResult.ok(new PlanRequest(dayCount, strategy, chosen, amount));
    }

    /**
     * Parses a plan length (also used by the "New plan" control).
     *
     * @param text a whole number from 1 to 7
     * @return the number of days, or one error for the field "days" with the domain's message
     */
    public static FormResult<Integer> parseDays(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!WHOLE.matcher(trimmed).matches()) {
            return FormResult.failed(List.of(new FieldError("days", ProfileForm.NOT_A_NUMBER)));
        }
        int days = Integer.parseInt(trimmed);
        List<FieldError> errors = new ArrayList<>();
        PlanRequest defaults = PlanRequest.defaults();
        check("days", () -> new PlanRequest(days, defaults.strategy(), defaults.slots(), null), errors);
        return errors.isEmpty() ? FormResult.ok(days) : FormResult.failed(errors);
    }

    /** Builds a request with one value under test to obtain the domain's message for it. */
    private static void check(String field, Runnable build, List<FieldError> errors) {
        try {
            build.run();
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError(field, e.getMessage()));
        }
    }
}
