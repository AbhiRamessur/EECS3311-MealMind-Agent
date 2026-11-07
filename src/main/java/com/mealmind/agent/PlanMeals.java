package com.mealmind.agent;

import com.mealmind.model.Meal;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import java.util.List;
import java.util.Objects;

/**
 * Builds the meal that goes into a plan slot for one recipe. Shared by the CLI's {@code plan set} and
 * {@link PlanEditor}, so a manual edit and an AI-interpreted edit produce exactly the same meal.
 */
public final class PlanMeals {

    private PlanMeals() {
    }

    /**
     * Wraps a recipe in a meal labelled with the recipe name, so undo/redo descriptions read
     * "Replace Day 1 DINNER with &lt;recipe&gt;".
     *
     * @param type   the slot
     * @param recipe the recipe, already scaled if needed
     * @return the meal
     * @throws NullPointerException if an argument is null
     */
    public static Meal forRecipe(MealType type, Recipe recipe) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(recipe, "recipe");
        return new Meal(type, recipe.getName(), List.of(recipe));
    }
}
