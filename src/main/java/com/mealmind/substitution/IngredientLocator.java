package com.mealmind.substitution;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Names;
import com.mealmind.model.Recipe;
import com.mealmind.planning.LeftoverStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Finds the ingredient of a recipe that the user named. An exact name (ignoring case and extra spaces)
 * wins; otherwise the name must match exactly one ingredient by whole words, using the same rule as
 * F10 ({@link LeftoverStrategy#matches}), so "chicken" finds "Chicken breast".
 */
public final class IngredientLocator {

    private IngredientLocator() {
    }

    /**
     * Returns the position of the named ingredient.
     *
     * @param recipe the recipe
     * @param name   the ingredient as the user named it
     * @return the 0-based index
     * @throws IllegalArgumentException if the name is blank, matches nothing or matches several ingredients
     * @throws NullPointerException     if {@code recipe} is null
     */
    public static int find(Recipe recipe, String name) {
        Objects.requireNonNull(recipe, "recipe");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }
        String wanted = Names.normalize(name);
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            if (Names.normalize(ingredients.get(i).getName()).equals(wanted)) {
                return i;
            }
        }
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            if (LeftoverStrategy.matches(Names.normalize(ingredients.get(i).getName()), wanted)) {
                matches.add(i);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Recipe " + recipe.getId() + " has no ingredient '" + name.trim()
                    + "'. Ingredients: " + names(ingredients.stream().map(Ingredient::getName).toList()));
        }
        throw new IllegalArgumentException("'" + name.trim() + "' matches several ingredients: "
                + names(matches.stream().map(i -> ingredients.get(i).getName()).toList()) + ". Be more specific.");
    }

    private static String names(List<String> names) {
        return names.stream().collect(Collectors.joining(", "));
    }
}
