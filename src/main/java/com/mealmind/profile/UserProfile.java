package com.mealmind.profile;

import com.mealmind.model.Names;
import com.mealmind.model.ObservableSubject;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The user's dietary profile (F01): diet type, allergens, optional daily nutrition targets, an
 * optional weekly budget and the household size.
 *
 * <p>A ConcreteSubject in the Observer pattern: each setter notifies observers once, and only when
 * the value actually changes, so views and the autosave observer react to real changes only.
 * Every value is range-checked; an invalid value throws {@link IllegalArgumentException} and changes
 * nothing. Profile edits are settings, not plan state, so they are not routed through the undo history.
 */
public final class UserProfile extends ObservableSubject {

    /** Allowed daily calorie target range. */
    public static final int MIN_CALORIES = 800;
    /** Allowed daily calorie target range. */
    public static final int MAX_CALORIES = 6000;
    /** Largest allowed daily macro target in grams. */
    public static final double MAX_MACRO_G = 1000;
    /** Largest allowed weekly budget. */
    public static final double MAX_WEEKLY_BUDGET = 10000;
    /** Largest allowed household size. */
    public static final int MAX_HOUSEHOLD_SERVINGS = 12;

    private DietType diet = DietType.OMNIVORE;
    private final SortedSet<String> allergens = new TreeSet<>();
    private Integer calorieTarget;
    private Double proteinTargetG;
    private Double carbsTargetG;
    private Double fatTargetG;
    private Double weeklyBudget;
    private int householdServings = 1;

    // ---- validation (shared by the setters and applyData) ----

    private static void checkCalories(Integer value) {
        if (value != null && (value < MIN_CALORIES || value > MAX_CALORIES)) {
            throw new IllegalArgumentException("Calorie target must be between " + MIN_CALORIES + " and "
                    + MAX_CALORIES + " kcal, got " + value);
        }
    }

    private static void checkMacro(Double value, String field) {
        if (value != null && !(Double.isFinite(value) && value > 0 && value <= MAX_MACRO_G)) {
            throw new IllegalArgumentException(field + " target must be greater than 0 and at most "
                    + (int) MAX_MACRO_G + " g, got " + value);
        }
    }

    private static void checkBudget(Double value) {
        if (value != null && !(Double.isFinite(value) && value > 0 && value <= MAX_WEEKLY_BUDGET)) {
            throw new IllegalArgumentException("Weekly budget must be greater than 0 and at most "
                    + (int) MAX_WEEKLY_BUDGET + ", got " + value);
        }
    }

    private static void checkServings(int value) {
        if (value < 1 || value > MAX_HOUSEHOLD_SERVINGS) {
            throw new IllegalArgumentException("Household servings must be between 1 and "
                    + MAX_HOUSEHOLD_SERVINGS + ", got " + value);
        }
    }

    private static String normalizeAllergen(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Allergen name must not be blank");
        }
        return Names.normalize(name);
    }

    // ---- getters ----

    /**
     * Returns the diet type.
     *
     * @return the diet, never null
     */
    public DietType getDiet() {
        return diet;
    }

    /**
     * Returns the allergens, normalized (lowercase, single spaces) and sorted.
     *
     * @return an unmodifiable snapshot
     */
    public Set<String> getAllergens() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(allergens));
    }

    /**
     * Returns the daily calorie target.
     *
     * @return the target in kcal, or empty if not set
     */
    public Optional<Integer> getCalorieTarget() {
        return Optional.ofNullable(calorieTarget);
    }

    /**
     * Returns the daily protein target.
     *
     * @return the target in grams, or empty if not set
     */
    public Optional<Double> getProteinTargetG() {
        return Optional.ofNullable(proteinTargetG);
    }

    /**
     * Returns the daily carbohydrate target.
     *
     * @return the target in grams, or empty if not set
     */
    public Optional<Double> getCarbsTargetG() {
        return Optional.ofNullable(carbsTargetG);
    }

    /**
     * Returns the daily fat target.
     *
     * @return the target in grams, or empty if not set
     */
    public Optional<Double> getFatTargetG() {
        return Optional.ofNullable(fatTargetG);
    }

    /**
     * Returns the weekly food budget.
     *
     * @return the budget, or empty if not set
     */
    public Optional<Double> getWeeklyBudget() {
        return Optional.ofNullable(weeklyBudget);
    }

    /**
     * Returns how many people the user cooks for.
     *
     * @return the household size, from 1 to {@value #MAX_HOUSEHOLD_SERVINGS}
     */
    public int getHouseholdServings() {
        return householdServings;
    }

    // ---- setters: each notifies once, only on an actual change ----

    /**
     * Sets the diet type.
     *
     * @param newDiet the diet
     * @throws NullPointerException if {@code newDiet} is null
     */
    public void setDiet(DietType newDiet) {
        Objects.requireNonNull(newDiet, "diet");
        if (newDiet != diet) {
            diet = newDiet;
            notifyObservers("Diet set to " + newDiet.getDisplayName());
        }
    }

    /**
     * Adds an allergen. Names are normalized, so {@code "  Peanuts "} and {@code "peanuts"} are the same.
     *
     * @param name the allergen
     * @return {@code true} if it was added, {@code false} if it was already listed
     * @throws IllegalArgumentException if {@code name} is null or blank
     */
    public boolean addAllergen(String name) {
        String normalized = normalizeAllergen(name);
        if (!allergens.add(normalized)) {
            return false;
        }
        notifyObservers("Allergen added: " + normalized);
        return true;
    }

    /**
     * Removes an allergen.
     *
     * @param name the allergen, matched after normalization
     * @return {@code true} if it was removed, {@code false} if it was not listed
     * @throws IllegalArgumentException if {@code name} is null or blank
     */
    public boolean removeAllergen(String name) {
        String normalized = normalizeAllergen(name);
        if (!allergens.remove(normalized)) {
            return false;
        }
        notifyObservers("Allergen removed: " + normalized);
        return true;
    }

    /**
     * Sets or clears the daily calorie target.
     *
     * @param value kcal from {@value #MIN_CALORIES} to {@value #MAX_CALORIES}, or null to clear
     * @throws IllegalArgumentException if out of range
     */
    public void setCalorieTarget(Integer value) {
        checkCalories(value);
        if (!Objects.equals(value, calorieTarget)) {
            calorieTarget = value;
            notifyObservers(describeChange("Calorie target", value));
        }
    }

    /**
     * Sets or clears the daily protein target.
     *
     * @param value grams, greater than 0 and at most 1000, or null to clear
     * @throws IllegalArgumentException if out of range, NaN or infinite
     */
    public void setProteinTargetG(Double value) {
        checkMacro(value, "Protein");
        if (!Objects.equals(value, proteinTargetG)) {
            proteinTargetG = value;
            notifyObservers(describeChange("Protein target", value));
        }
    }

    /**
     * Sets or clears the daily carbohydrate target.
     *
     * @param value grams, greater than 0 and at most 1000, or null to clear
     * @throws IllegalArgumentException if out of range, NaN or infinite
     */
    public void setCarbsTargetG(Double value) {
        checkMacro(value, "Carbs");
        if (!Objects.equals(value, carbsTargetG)) {
            carbsTargetG = value;
            notifyObservers(describeChange("Carbs target", value));
        }
    }

    /**
     * Sets or clears the daily fat target.
     *
     * @param value grams, greater than 0 and at most 1000, or null to clear
     * @throws IllegalArgumentException if out of range, NaN or infinite
     */
    public void setFatTargetG(Double value) {
        checkMacro(value, "Fat");
        if (!Objects.equals(value, fatTargetG)) {
            fatTargetG = value;
            notifyObservers(describeChange("Fat target", value));
        }
    }

    /**
     * Sets or clears the weekly budget.
     *
     * @param value greater than 0 and at most 10000, or null to clear
     * @throws IllegalArgumentException if out of range, NaN or infinite
     */
    public void setWeeklyBudget(Double value) {
        checkBudget(value);
        if (!Objects.equals(value, weeklyBudget)) {
            weeklyBudget = value;
            notifyObservers(describeChange("Weekly budget", value));
        }
    }

    /**
     * Sets the household size.
     *
     * @param value from 1 to {@value #MAX_HOUSEHOLD_SERVINGS}
     * @throws IllegalArgumentException if out of range
     */
    public void setHouseholdServings(int value) {
        checkServings(value);
        if (value != householdServings) {
            householdServings = value;
            notifyObservers("Household servings set to " + value);
        }
    }

    private static String describeChange(String field, Object value) {
        return value == null ? field + " cleared" : field + " set to " + value;
    }

    // ---- snapshots ----

    /**
     * Returns an immutable snapshot of the profile.
     *
     * @return the current values
     */
    public ProfileData toData() {
        return new ProfileData(diet, allergens, calorieTarget, proteinTargetG, carbsTargetG, fatTargetG,
                weeklyBudget, householdServings);
    }

    /**
     * Replaces every setting at once. All values are validated first, so an invalid snapshot leaves
     * the profile unchanged and notifies no one. Observers are notified once if anything changed.
     *
     * @param data the new values
     * @throws IllegalArgumentException if any value is invalid
     * @throws NullPointerException     if {@code data} or its diet is null
     */
    public void applyData(ProfileData data) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(data.diet(), "diet");
        SortedSet<String> newAllergens = new TreeSet<>();
        for (String allergen : data.allergens()) {
            newAllergens.add(normalizeAllergen(allergen));
        }
        checkCalories(data.calorieTarget());
        checkMacro(data.proteinTargetG(), "Protein");
        checkMacro(data.carbsTargetG(), "Carbs");
        checkMacro(data.fatTargetG(), "Fat");
        checkBudget(data.weeklyBudget());
        checkServings(data.householdServings());

        ProfileData before = toData();
        diet = data.diet();
        allergens.clear();
        allergens.addAll(newAllergens);
        calorieTarget = data.calorieTarget();
        proteinTargetG = data.proteinTargetG();
        carbsTargetG = data.carbsTargetG();
        fatTargetG = data.fatTargetG();
        weeklyBudget = data.weeklyBudget();
        householdServings = data.householdServings();
        if (!toData().equals(before)) {
            notifyObservers("Profile updated");
        }
    }
}
