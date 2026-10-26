package com.mealmind.command;

import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Empties one slot of a {@link MealPlan} (the CLI's {@code plan clear}). Undo puts the removed meal
 * back. {@link ReplaceMealCommand} cannot do this because it always sets a meal.
 */
public final class ClearMealCommand extends AbstractCommand {

    private final MealPlan plan;
    private final int day;
    private final MealType type;
    private Meal removed;

    /**
     * Creates the command.
     *
     * @param plan the receiver
     * @param day  the day number in the plan
     * @param type the meal slot to empty
     * @throws NullPointerException     if {@code plan} or {@code type} is null
     * @throws IllegalArgumentException if {@code day} is not a day of {@code plan}
     */
    public ClearMealCommand(MealPlan plan, int day, MealType type) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.type = Objects.requireNonNull(type, "type");
        plan.getDay(day); // validates the day number now rather than at execute time
        this.day = day;
    }

    /**
     * Empties the slot.
     *
     * @throws NoSuchElementException if the slot is already empty; nothing is changed
     */
    @Override
    protected void doExecute() {
        removed = plan.clearMeal(day, type)
                .orElseThrow(() -> new NoSuchElementException("Day " + day + " " + type + " is already empty"));
    }

    @Override
    protected void doUndo() {
        plan.setMeal(day, type, removed);
    }

    @Override
    public String description() {
        return "Clear Day " + day + " " + type;
    }
}
