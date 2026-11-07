package com.mealmind.gui.logic;

import com.mealmind.model.MealType;

/**
 * A manual meal assignment from the Plan tab (the GUI's {@code plan set}).
 *
 * @param day      the day number
 * @param slot     the slot
 * @param recipeId the recipe id
 * @param servings the servings to scale to, or null to keep the recipe's own (as {@code plan set} without
 *                 {@code --servings})
 */
public record PlanAssignment(int day, MealType slot, String recipeId, Integer servings) {
}
