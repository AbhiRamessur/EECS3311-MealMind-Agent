package com.mealmind.gui.logic;

import com.mealmind.model.MealType;

/**
 * One slot of the plan grid.
 *
 * @param day      the day number, from 1
 * @param slot     the meal slot
 * @param text     the meal as {@code plan show} describes it, or "(empty)"
 * @param filled   whether the slot holds a meal
 * @param recipeId the first recipe's id, or null when empty or not a recipe meal
 */
public record PlanCell(int day, MealType slot, String text, boolean filled, String recipeId) {
}
