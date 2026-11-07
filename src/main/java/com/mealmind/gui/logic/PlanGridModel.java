package com.mealmind.gui.logic;

import com.mealmind.cli.CLIHandler;
import com.mealmind.cli.CliFormat;
import com.mealmind.model.DayPlan;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.ValidationResult;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the Plan tab's grid from a {@link MealPlan}, with the CLI's {@code plan show} wording for meals and
 * totals ({@link CLIHandler#describe}, {@link CliFormat#nutrition}) and the validator's advisory/safety split.
 */
public final class PlanGridModel {

    /** Shown for an empty slot, as in {@code plan show}. */
    public static final String EMPTY = "(empty)";
    private static final List<String> WEEKDAYS =
            List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private PlanGridModel() {
    }

    /**
     * Builds the grid.
     *
     * @param plan      the plan to show
     * @param validator the checks against the current profile
     * @return the grid
     * @throws NullPointerException if an argument is null
     */
    public static PlanGrid of(MealPlan plan, ConstraintValidator validator) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(validator, "validator");
        List<PlanDayRow> rows = new ArrayList<>();
        for (DayPlan day : plan.getDays()) {
            List<PlanCell> cells = new ArrayList<>();
            for (MealType slot : MealType.values()) {
                Optional<Meal> meal = day.getMeal(slot);
                cells.add(new PlanCell(day.getDayNumber(), slot, meal.map(CLIHandler::describe).orElse(EMPTY), meal.isPresent(),
                        meal.flatMap(m -> m.getRecipes().stream().findFirst()).map(Recipe::getId).orElse(null)));
            }
            String totals = day.getFilledSlots().isEmpty() ? ""
                    : String.format(Locale.ROOT, "%.0f kcal | $%.2f", day.getCalories(), day.getCost());
            rows.add(new PlanDayRow(day.getDayNumber(), dayLabel(day.getDayNumber()), cells, totals));
        }
        ValidationResult result = validator.validateMacrosAndAllergens(plan);
        return new PlanGrid(plan.getNumberOfDays(), rows, "Plan totals: " + CliFormat.nutrition(plan, 1),
                result.getAdvisories().stream().map(Violation::message).toList(),
                result.getSafetyViolations().stream().map(Violation::message).toList());
    }

    /**
     * Returns the label of a day, with Day 1 as Monday (plans have day numbers, not dates).
     *
     * @param day the day number, 1 to 7
     * @return e.g. "Day 2 (Tuesday)"
     */
    public static String dayLabel(int day) {
        return "Day " + day + " (" + WEEKDAYS.get(day - 1) + ")";
    }
}
