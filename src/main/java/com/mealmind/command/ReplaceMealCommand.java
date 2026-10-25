package com.mealmind.command;

import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import java.util.Objects;
import java.util.Optional;

/**
 * Puts a meal into one slot of a {@link MealPlan} (F06 / UC06, and later F10). Undo puts the
 * previous meal back, or empties the slot if it was empty before.
 *
 * <p>Checking that {@code newMeal} is safe for the user (allergens, macro limits) is the caller's
 * responsibility; the {@code ConstraintValidator} that does this arrives in a later commit.
 */
public final class ReplaceMealCommand extends AbstractCommand {

    private final MealPlan plan;
    private final int day;
    private final MealType type;
    private final Meal newMeal;
    private Optional<Meal> previous = Optional.empty();

    /**
     * Creates the command.
     *
     * @param plan    the receiver
     * @param day     the day number in the plan
     * @param type    the meal slot
     * @param newMeal the meal to put there
     * @throws NullPointerException     if {@code plan}, {@code type} or {@code newMeal} is null
     * @throws IllegalArgumentException if {@code day} is not a day of {@code plan}
     */
    public ReplaceMealCommand(MealPlan plan, int day, MealType type, Meal newMeal) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.type = Objects.requireNonNull(type, "type");
        this.newMeal = Objects.requireNonNull(newMeal, "newMeal");
        plan.getDay(day); // validates the day number now rather than at execute time
        this.day = day;
    }

    @Override
    protected void doExecute() {
        previous = plan.setMeal(day, type, newMeal);
    }

    @Override
    protected void doUndo() {
        previous.ifPresentOrElse(meal -> plan.setMeal(day, type, meal), () -> plan.clearMeal(day, type));
    }

    @Override
    public String description() {
        return "Replace Day " + day + " " + type + " with " + newMeal.getName();
    }
}
