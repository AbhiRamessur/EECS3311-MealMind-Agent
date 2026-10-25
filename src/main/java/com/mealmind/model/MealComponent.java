package com.mealmind.model;

import java.util.List;

/**
 * The Component role of the <strong>Composite pattern</strong> used for MealMind's meal model.
 *
 * <p>Roles: {@code MealComponent} is the Component; {@link Ingredient} is the Leaf; {@link Recipe},
 * {@link Meal}, {@link DayPlan} and {@link MealPlan} are Composites. A composite's totals are the
 * sums of its children's totals, so client code can ask any level, from one ingredient up to a
 * week-long plan, for {@code getCalories()} or {@code getCost()} with the same call.
 *
 * <p>This is the <em>safe</em> variant of Composite: methods for managing children (such as
 * {@link DayPlan#setMeal}) exist only on the composite classes, not on this interface. A leaf can
 * therefore never be asked to add a child, at the cost of clients needing the concrete type to
 * change a structure.
 *
 * <p>All nutrition and cost values are totals for the whole component, not per serving.
 */
public interface MealComponent {

    /**
     * Returns a display name for this component.
     *
     * @return the name
     */
    String getName();

    /**
     * Returns the total kcal of this component.
     *
     * @return the calories
     */
    double getCalories();

    /**
     * Returns the total protein of this component.
     *
     * @return the protein in grams
     */
    double getProteinG();

    /**
     * Returns the total carbohydrates of this component.
     *
     * @return the carbohydrates in grams
     */
    double getCarbsG();

    /**
     * Returns the total fat of this component.
     *
     * @return the fat in grams
     */
    double getFatG();

    /**
     * Returns the total cost of this component.
     *
     * @return the cost
     */
    double getCost();

    /**
     * Returns every leaf ingredient beneath this component, flattened in traversal order.
     * Duplicates are kept; nothing is merged.
     *
     * @return an unmodifiable list of ingredients
     */
    List<Ingredient> getIngredients();

    /**
     * Returns every recipe found anywhere beneath this component, in traversal order.
     *
     * @return an unmodifiable list of recipes; empty by default
     */
    default List<Recipe> getRecipes() {
        return List.of();
    }
}
