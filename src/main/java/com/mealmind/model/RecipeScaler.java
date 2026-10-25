package com.mealmind.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Scales recipes to a different number of servings (F08) and rounds quantities for display.
 *
 * <p>Scaling and rounding are deliberately separate. {@link #scale} is exact, with no rounding, so
 * nutrition and cost totals stay proportional and per-serving values are unchanged.
 * {@link #roundForDisplay} only formats a number for the user and is never applied to stored values.
 */
public final class RecipeScaler {

    // Implements the Stage 1 F08 rule "non-scalable items (pinch of salt) preserved via regular
    // expressions": an ingredient whose name contains one of these phrases keeps its quantity when
    // the recipe is scaled, because "a pinch" or "to taste" does not grow with the serving count.
    private static final Pattern NON_SCALABLE =
            Pattern.compile("\\b(to taste|pinch|dash|as needed|for (serving|garnish|frying))\\b",
                    Pattern.CASE_INSENSITIVE);

    private RecipeScaler() {
    }

    /**
     * Returns a copy of {@code recipe} scaled to {@code targetServings}. Each scalable ingredient's
     * quantity, calories, macros and cost are multiplied by {@code targetServings / servings};
     * non-scalable ingredients (see {@link #isScalable}) are kept as they are. The id, name, prep time
     * and instructions are unchanged. The original recipe is not modified.
     *
     * @param recipe         the recipe to scale
     * @param targetServings the new number of servings, {@code >= 1}
     * @return the scaled recipe, or {@code recipe} itself if the servings are already equal
     * @throws IllegalArgumentException if {@code targetServings < 1}
     * @throws NullPointerException     if {@code recipe} is null
     */
    public static Recipe scale(Recipe recipe, int targetServings) {
        Objects.requireNonNull(recipe, "recipe");
        if (targetServings < 1) {
            throw new IllegalArgumentException("Target servings must be at least 1, got " + targetServings);
        }
        if (targetServings == recipe.getServings()) {
            return recipe;
        }
        double factor = (double) targetServings / recipe.getServings();
        List<Ingredient> scaled = recipe.getIngredients().stream()
                .map(ingredient -> isScalable(ingredient)
                        ? ingredient.withQuantity(ingredient.getQuantity() * factor)
                        : ingredient)
                .toList();
        return new Recipe(recipe.getId(), recipe.getName(), targetServings, recipe.getPrepMinutes(),
                recipe.getInstructions(), scaled);
    }

    /**
     * Returns whether an ingredient's quantity should change when its recipe is scaled.
     * Names such as "Salt, to taste", "Pinch of salt" or "Parsley for garnish" are not scalable.
     *
     * @param ingredient the ingredient to check
     * @return {@code false} if the name matches a non-scalable phrase, otherwise {@code true}
     * @throws NullPointerException if {@code ingredient} is null
     */
    public static boolean isScalable(Ingredient ingredient) {
        Objects.requireNonNull(ingredient, "ingredient");
        return !NON_SCALABLE.matcher(ingredient.getName()).find();
    }

    /**
     * Rounds a quantity to a sensible culinary precision, for presentation only:
     * tsp, tbsp, cup and pieces to the nearest 0.25; g and ml to a whole number from 10 upwards,
     * otherwise to one decimal place; kg and l to two decimal places.
     *
     * @param quantity the exact quantity, finite and {@code >= 0}
     * @param unit     the unit the quantity is in
     * @return the rounded quantity
     * @throws IllegalArgumentException if {@code quantity} is negative, NaN or infinite
     * @throws NullPointerException     if {@code unit} is null
     */
    public static double roundForDisplay(double quantity, Unit unit) {
        Objects.requireNonNull(unit, "unit");
        if (!Double.isFinite(quantity) || quantity < 0) {
            throw new IllegalArgumentException("Quantity must be a finite, non-negative number, got " + quantity);
        }
        return switch (unit) {
            case TEASPOON, TABLESPOON, CUP, COUNT -> roundToFraction(quantity, 4);
            case GRAM, MILLILITER -> quantity >= 10 ? Math.round(quantity) : roundToFraction(quantity, 10);
            case KILOGRAM, LITER -> roundToFraction(quantity, 100);
        };
    }

    /** Rounds to the nearest 1/{@code parts} (e.g. parts = 4 gives quarters). */
    private static double roundToFraction(double quantity, int parts) {
        return Math.round(quantity * parts) / (double) parts;
    }
}
