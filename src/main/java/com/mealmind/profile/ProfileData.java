package com.mealmind.profile;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * An immutable snapshot of a {@link UserProfile}, used for saving and for replacing several settings
 * in one step. A {@code null} target or budget means "not set". Values are not validated here;
 * {@link UserProfile#applyData} validates them.
 *
 * @param diet              the diet type
 * @param allergens         allergen names (copied into a sorted, unmodifiable set; null means none)
 * @param calorieTarget     daily calorie target, or null
 * @param proteinTargetG    daily protein target in grams, or null
 * @param carbsTargetG      daily carbohydrate target in grams, or null
 * @param fatTargetG        daily fat target in grams, or null
 * @param weeklyBudget      weekly food budget, or null
 * @param householdServings number of people cooked for
 */
public record ProfileData(DietType diet, Set<String> allergens, Integer calorieTarget, Double proteinTargetG,
                          Double carbsTargetG, Double fatTargetG, Double weeklyBudget, int householdServings) {

    /** Copies the allergen set so later changes to the argument have no effect. */
    public ProfileData {
        allergens = allergens == null ? Set.of() : Collections.unmodifiableSortedSet(new TreeSet<>(allergens));
    }

    /**
     * Returns the default profile: omnivore, no allergens, no targets, no budget, one serving.
     *
     * @return the defaults
     */
    public static ProfileData defaults() {
        return new ProfileData(DietType.OMNIVORE, Set.of(), null, null, null, null, null, 1);
    }
}
