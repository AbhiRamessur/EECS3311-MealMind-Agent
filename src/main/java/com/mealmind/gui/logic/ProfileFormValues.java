package com.mealmind.gui.logic;

import java.util.List;

/**
 * The texts the Profile tab shows for a profile; blank means "not set".
 *
 * @param diet      the diet's display name, e.g. "Vegetarian"
 * @param allergens the allergen names in order (unmodifiable)
 * @param calories  daily calories, or blank
 * @param protein   daily protein grams, or blank
 * @param carbs     daily carbohydrate grams, or blank
 * @param fat       daily fat grams, or blank
 * @param budget    weekly budget, or blank
 * @param servings  household servings
 */
public record ProfileFormValues(String diet, List<String> allergens, String calories, String protein, String carbs,
                                String fat, String budget, String servings) {

    /** Copies the allergen list. */
    public ProfileFormValues {
        allergens = List.copyOf(allergens);
    }
}
