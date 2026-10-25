package com.mealmind.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A multi-day meal plan: the root Composite in the {@link MealComponent} pattern. It owns
 * {@link DayPlan}s numbered 1 to {@link #getNumberOfDays()}, created by the constructor.
 *
 * <p>Totals are the sums over all days. The plan is mutable: meals are set and cleared through
 * this class or the individual days.
 *
 * <p>It is also a ConcreteSubject in the Observer pattern. Because callers can change a day
 * directly (through {@link #getDay(int)}), the plan observes each of its own days and re-notifies
 * its observers whenever one changes. {@link #setMeal} and {@link #clearMeal} simply delegate to the
 * day, so each change produces exactly one plan notification.
 */
public final class MealPlan extends ObservableSubject implements MealComponent {

    /**
     * One meal slot in a plan, such as day 2's dinner.
     *
     * @param day  the day number
     * @param type the meal slot on that day
     */
    public record Slot(int day, MealType type) {

        /**
         * Validates the slot.
         *
         * @throws NullPointerException if {@code type} is null
         */
        public Slot {
            Objects.requireNonNull(type, "type");
        }
    }

    /** Forwards each day's change notifications to the plan's own observers. */
    private final class DayChangeForwarder implements ModelObserver {
        @Override
        public void onStateChanged(ChangeEvent event) {
            notifyObservers(event.description());
        }
    }

    private final String name;
    private final List<DayPlan> days;

    /**
     * Creates a plan with empty days numbered 1 to {@code numberOfDays}.
     *
     * @param name         display name; trimmed, must not be blank
     * @param numberOfDays how many days the plan covers, from 1 to {@value DayPlan#MAX_DAYS}
     * @throws IllegalArgumentException if {@code name} is blank or {@code numberOfDays} is out of range
     */
    public MealPlan(String name, int numberOfDays) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Meal plan name must not be blank");
        }
        if (numberOfDays < 1 || numberOfDays > DayPlan.MAX_DAYS) {
            throw new IllegalArgumentException("Number of days must be between 1 and " + DayPlan.MAX_DAYS
                    + ", got " + numberOfDays);
        }
        this.name = name.trim();
        DayChangeForwarder forwarder = new DayChangeForwarder();
        List<DayPlan> created = new ArrayList<>();
        for (int day = 1; day <= numberOfDays; day++) {
            DayPlan dayPlan = new DayPlan(day);
            dayPlan.addObserver(forwarder);
            created.add(dayPlan);
        }
        this.days = List.copyOf(created);
    }

    /**
     * Returns how many days the plan covers.
     *
     * @return the number of days, from 1 to {@value DayPlan#MAX_DAYS}
     */
    public int getNumberOfDays() {
        return days.size();
    }

    /**
     * Returns one day of the plan.
     *
     * @param dayNumber the day, from 1 to {@link #getNumberOfDays()}
     * @return that day
     * @throws IllegalArgumentException if {@code dayNumber} is out of range
     */
    public DayPlan getDay(int dayNumber) {
        if (dayNumber < 1 || dayNumber > days.size()) {
            throw new IllegalArgumentException("Day must be between 1 and " + days.size() + ", got " + dayNumber);
        }
        return days.get(dayNumber - 1);
    }

    /**
     * Returns all days in order.
     *
     * @return an unmodifiable list of days
     */
    public List<DayPlan> getDays() {
        return days;
    }

    /**
     * Returns the meal in one slot.
     *
     * @param day  the day number
     * @param type the slot
     * @return the meal, or empty if the slot is not filled
     * @throws IllegalArgumentException if {@code day} is out of range
     */
    public Optional<Meal> getMeal(int day, MealType type) {
        return getDay(day).getMeal(type);
    }

    /**
     * Puts a meal into one slot, replacing any meal already there.
     *
     * @param day  the day number
     * @param type the slot
     * @param meal the meal to put there
     * @return the meal that was replaced, or empty if the slot was empty
     * @throws IllegalArgumentException if {@code day} is out of range
     */
    public Optional<Meal> setMeal(int day, MealType type, Meal meal) {
        return getDay(day).setMeal(type, meal);
    }

    /**
     * Empties one slot.
     *
     * @param day  the day number
     * @param type the slot
     * @return the meal that was removed, or empty if the slot was already empty
     * @throws IllegalArgumentException if {@code day} is out of range
     */
    public Optional<Meal> clearMeal(int day, MealType type) {
        return getDay(day).clearMeal(type);
    }

    /**
     * Returns every unfilled slot in the plan.
     *
     * @return an unmodifiable list ordered by day, then by {@link MealType}
     */
    public List<Slot> getEmptySlots() {
        List<Slot> empty = new ArrayList<>();
        for (DayPlan day : days) {
            for (MealType type : day.getEmptySlots()) {
                empty.add(new Slot(day.getDayNumber(), type));
            }
        }
        return List.copyOf(empty);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getCalories() {
        return MealComponents.sum(days, MealComponent::getCalories);
    }

    @Override
    public double getProteinG() {
        return MealComponents.sum(days, MealComponent::getProteinG);
    }

    @Override
    public double getCarbsG() {
        return MealComponents.sum(days, MealComponent::getCarbsG);
    }

    @Override
    public double getFatG() {
        return MealComponents.sum(days, MealComponent::getFatG);
    }

    @Override
    public double getCost() {
        return MealComponents.sum(days, MealComponent::getCost);
    }

    @Override
    public List<Ingredient> getIngredients() {
        return MealComponents.ingredientsOf(days);
    }

    @Override
    public List<Recipe> getRecipes() {
        return MealComponents.recipesOf(days);
    }

    /**
     * Returns every ingredient the plan needs, flattened and <strong>not consolidated</strong>:
     * the same ingredient appears once per use. Same as {@link #getIngredients()}; named to match
     * the Stage 1 traceability matrix. Merging duplicates and subtracting pantry stock is the job
     * of {@code ShoppingListGenerator}, added in a later commit.
     *
     * @return an unmodifiable list of ingredients
     */
    public List<Ingredient> getRequiredIngredients() {
        return getIngredients();
    }

    /**
     * Returns every recipe used in the plan, in order, including repeats. Same as
     * {@link #getRecipes()}; named to match the Stage 1 traceability matrix.
     *
     * @return an unmodifiable list of recipes
     */
    public List<Recipe> getConstituentRecipes() {
        return getRecipes();
    }

    @Override
    public String toString() {
        return "MealPlan{name=" + name + ", days=" + days.size() + "}";
    }
}
