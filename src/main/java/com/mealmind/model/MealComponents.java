package com.mealmind.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Shared aggregation logic for the composite classes, so each one does not repeat the same loops.
 */
final class MealComponents {

    private MealComponents() {
    }

    /** Sums one value (e.g. calories) over the given children. */
    static double sum(Collection<? extends MealComponent> children, ToDoubleFunction<MealComponent> value) {
        return children.stream().mapToDouble(value).sum();
    }

    /** Concatenates the children's leaf ingredients, in order. */
    static List<Ingredient> ingredientsOf(Collection<? extends MealComponent> children) {
        List<Ingredient> result = new ArrayList<>();
        children.forEach(child -> result.addAll(child.getIngredients()));
        return List.copyOf(result);
    }

    /** Concatenates the children's recipes, in order. */
    static List<Recipe> recipesOf(Collection<? extends MealComponent> children) {
        List<Recipe> result = new ArrayList<>();
        children.forEach(child -> result.addAll(child.getRecipes()));
        return List.copyOf(result);
    }
}
