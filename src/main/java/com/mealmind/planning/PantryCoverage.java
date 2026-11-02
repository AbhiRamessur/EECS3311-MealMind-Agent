package com.mealmind.planning;

import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.pantry.Pantry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How much of a recipe the pantry already covers. Shared by the {@code recipe_search} tool and the
 * {@link PantryFirstStrategy}, so both use the same rule.
 *
 * <p>An ingredient is covered when the pantry holds at least the required quantity in a compatible unit;
 * incompatible units count as not covered. Expiry is ignored. Read-only.
 */
public final class PantryCoverage {

    private static final double EPSILON = 1e-9;

    private PantryCoverage() {
    }

    /**
     * Pantry coverage of one recipe.
     *
     * @param percent covered ingredients as a share of all ingredients, 0 to 100, rounded to the nearest int
     * @param missing the names of uncovered ingredients, in recipe order (unmodifiable)
     */
    public record Result(int percent, List<String> missing) {

        /**
         * Copies the list.
         *
         * @throws NullPointerException if {@code missing} is null
         */
        public Result {
            missing = List.copyOf(missing);
        }
    }

    /**
     * Computes the pantry coverage of {@code recipe}.
     *
     * @param recipe the recipe to check
     * @param pantry the pantry as it is now
     * @return the coverage
     * @throws NullPointerException if an argument is null
     */
    public static Result of(Recipe recipe, Pantry pantry) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(pantry, "pantry");
        List<String> missing = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!isCovered(ingredient, pantry)) {
                missing.add(ingredient.getName());
            }
        }
        int total = recipe.getIngredients().size();
        int percent = (int) Math.round((total - missing.size()) * 100.0 / total);
        return new Result(percent, missing);
    }

    private static boolean isCovered(Ingredient ingredient, Pantry pantry) {
        try {
            return pantry.availableQuantity(ingredient.getName(), ingredient.getUnit()) >= ingredient.getQuantity() - EPSILON;
        } catch (IncompatibleUnitException e) {
            return false;
        }
    }
}
