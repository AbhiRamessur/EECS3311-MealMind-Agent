package com.mealmind.gui.logic;

/**
 * One row of the Recipes tab's table, as display texts and flags.
 *
 * @param id           the recipe id
 * @param name         the recipe name
 * @param servingsText the servings, e.g. "4"
 * @param prepText     the prep time, e.g. "40 min"
 * @param caloriesText whole kcal per serving, e.g. "331"
 * @param ratingText   "4/5", or "-" when not rated
 * @param favorite     whether the user marked it as a favourite
 * @param bundled      whether it is a bundled sample recipe (cannot be removed)
 * @param safe         whether it passes the profile's diet and allergen check
 * @param safetyText   empty when safe, otherwise "Not safe for your profile"
 */
public record RecipeRow(String id, String name, String servingsText, String prepText, String caloriesText,
                        String ratingText, boolean favorite, boolean bundled, boolean safe, String safetyText) {
}
