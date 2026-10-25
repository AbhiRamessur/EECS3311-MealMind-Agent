package com.mealmind.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One day of a {@link MealPlan}: a mutable Composite in the {@link MealComponent} pattern holding
 * at most one {@link Meal} per {@link MealType} slot.
 *
 * <p>Totals are the sums over the filled slots; an empty day has all totals equal to zero.
 * Meals and slots are always visited in {@link MealType} order.
 *
 * <p>It is also a ConcreteSubject in the Observer pattern: observers are notified after every
 * {@link #setMeal} and after every {@link #clearMeal} that actually removes a meal.
 */
public final class DayPlan extends ObservableSubject implements MealComponent {

    /** The highest allowed day number, and the longest allowed plan. */
    public static final int MAX_DAYS = 7;

    private final int dayNumber;
    private final Map<MealType, Meal> meals = new EnumMap<>(MealType.class);

    /**
     * Creates an empty day.
     *
     * @param dayNumber the day's position in its plan, from 1 to {@value #MAX_DAYS}
     * @throws IllegalArgumentException if {@code dayNumber} is out of range
     */
    public DayPlan(int dayNumber) {
        if (dayNumber < 1 || dayNumber > MAX_DAYS) {
            throw new IllegalArgumentException("Day number must be between 1 and " + MAX_DAYS + ", got " + dayNumber);
        }
        this.dayNumber = dayNumber;
    }

    /**
     * Returns the day's position in its plan.
     *
     * @return the day number, from 1 to {@value #MAX_DAYS}
     */
    public int getDayNumber() {
        return dayNumber;
    }

    /**
     * Returns the meal in a slot.
     *
     * @param type the slot
     * @return the meal, or empty if the slot is not filled
     * @throws NullPointerException if {@code type} is null
     */
    public Optional<Meal> getMeal(MealType type) {
        return Optional.ofNullable(meals.get(Objects.requireNonNull(type, "type")));
    }

    /**
     * Puts a meal into a slot, replacing any meal already there, then notifies observers.
     *
     * @param type the slot
     * @param meal the meal to put there
     * @return the meal that was replaced, or empty if the slot was empty (used for undo)
     * @throws NullPointerException if {@code type} or {@code meal} is null
     */
    public Optional<Meal> setMeal(MealType type, Meal meal) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(meal, "meal");
        Optional<Meal> previous = Optional.ofNullable(meals.put(type, meal));
        notifyObservers(getName() + " " + type + " set to " + meal.getName());
        return previous;
    }

    /**
     * Empties a slot. Observers are notified only if a meal was actually removed.
     *
     * @param type the slot
     * @return the meal that was removed, or empty if the slot was already empty
     * @throws NullPointerException if {@code type} is null
     */
    public Optional<Meal> clearMeal(MealType type) {
        Optional<Meal> removed = Optional.ofNullable(meals.remove(Objects.requireNonNull(type, "type")));
        if (removed.isPresent()) {
            notifyObservers(getName() + " " + type + " cleared");
        }
        return removed;
    }

    /**
     * Returns the slots that hold a meal.
     *
     * @return an unmodifiable snapshot, in {@link MealType} order
     */
    public Set<MealType> getFilledSlots() {
        EnumSet<MealType> filled = EnumSet.noneOf(MealType.class);
        filled.addAll(meals.keySet());
        return Collections.unmodifiableSet(filled);
    }

    /**
     * Returns the slots that do not hold a meal.
     *
     * @return an unmodifiable list, in {@link MealType} order
     */
    public List<MealType> getEmptySlots() {
        return Arrays.stream(MealType.values()).filter(type -> !meals.containsKey(type)).toList();
    }

    /**
     * Returns {@code "Day N"}.
     *
     * @return the display name
     */
    @Override
    public String getName() {
        return "Day " + dayNumber;
    }

    @Override
    public double getCalories() {
        return MealComponents.sum(meals.values(), MealComponent::getCalories);
    }

    @Override
    public double getProteinG() {
        return MealComponents.sum(meals.values(), MealComponent::getProteinG);
    }

    @Override
    public double getCarbsG() {
        return MealComponents.sum(meals.values(), MealComponent::getCarbsG);
    }

    @Override
    public double getFatG() {
        return MealComponents.sum(meals.values(), MealComponent::getFatG);
    }

    @Override
    public double getCost() {
        return MealComponents.sum(meals.values(), MealComponent::getCost);
    }

    @Override
    public List<Ingredient> getIngredients() {
        return MealComponents.ingredientsOf(meals.values());
    }

    @Override
    public List<Recipe> getRecipes() {
        return MealComponents.recipesOf(meals.values());
    }

    @Override
    public String toString() {
        return "DayPlan{day=" + dayNumber + ", filled=" + meals.keySet() + "}";
    }
}
