package com.mealmind.model;

import java.util.List;

/**
 * An immutable recipe: a Composite of {@link Ingredient} leaves in the {@link MealComponent}
 * pattern.
 *
 * <p>Totals (calories, macros, cost) are the sums over the ingredients and describe the
 * <strong>whole recipe at its stated servings</strong>; the {@code ...PerServing()} methods divide
 * them by {@link #getServings()}. Because a recipe is immutable, scaling it to a different number
 * of servings produces a new {@code Recipe} rather than changing this one.
 *
 * <p>Two recipes are equal when they have the same {@link #getId() id}.
 */
public final class Recipe implements MealComponent {

    private final String id;
    private final String name;
    private final int servings;
    private final int prepMinutes;
    private final List<String> instructions;
    private final List<Ingredient> ingredients;

    /**
     * Creates a recipe. The lists are copied, so later changes to the arguments have no effect.
     *
     * @param id           unique identifier; trimmed, must not be blank
     * @param name         display name; trimmed, must not be blank
     * @param servings     number of servings the ingredients make, {@code >= 1}
     * @param prepMinutes  preparation time in minutes, {@code >= 0}
     * @param instructions the steps in order; may be empty
     * @param ingredients  the ingredients for all servings; must not be empty
     * @throws IllegalArgumentException if any argument is null or out of range
     */
    public Recipe(String id, String name, int servings, int prepMinutes, List<String> instructions,
                  List<Ingredient> ingredients) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Recipe id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipe name must not be blank");
        }
        if (servings < 1) {
            throw new IllegalArgumentException("Recipe servings must be at least 1, got " + servings);
        }
        if (prepMinutes < 0) {
            throw new IllegalArgumentException("Recipe prepMinutes must not be negative, got " + prepMinutes);
        }
        if (instructions == null) {
            throw new IllegalArgumentException("Recipe instructions must not be null");
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one ingredient");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.servings = servings;
        this.prepMinutes = prepMinutes;
        this.instructions = List.copyOf(instructions);
        this.ingredients = List.copyOf(ingredients);
    }

    /**
     * Returns the unique identifier.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns how many servings the ingredients make.
     *
     * @return the servings, at least 1
     */
    public int getServings() {
        return servings;
    }

    /**
     * Returns the preparation time.
     *
     * @return the time in minutes
     */
    public int getPrepMinutes() {
        return prepMinutes;
    }

    /**
     * Returns the preparation steps in order.
     *
     * @return an unmodifiable list, possibly empty
     */
    public List<String> getInstructions() {
        return instructions;
    }

    @Override
    public double getCalories() {
        return MealComponents.sum(ingredients, MealComponent::getCalories);
    }

    @Override
    public double getProteinG() {
        return MealComponents.sum(ingredients, MealComponent::getProteinG);
    }

    @Override
    public double getCarbsG() {
        return MealComponents.sum(ingredients, MealComponent::getCarbsG);
    }

    @Override
    public double getFatG() {
        return MealComponents.sum(ingredients, MealComponent::getFatG);
    }

    @Override
    public double getCost() {
        return MealComponents.sum(ingredients, MealComponent::getCost);
    }

    /**
     * Returns the calories in one serving.
     *
     * @return total calories divided by servings
     */
    public double caloriesPerServing() {
        return getCalories() / servings;
    }

    /**
     * Returns the protein in one serving.
     *
     * @return total protein in grams divided by servings
     */
    public double proteinPerServing() {
        return getProteinG() / servings;
    }

    /**
     * Returns the carbohydrates in one serving.
     *
     * @return total carbohydrates in grams divided by servings
     */
    public double carbsPerServing() {
        return getCarbsG() / servings;
    }

    /**
     * Returns the fat in one serving.
     *
     * @return total fat in grams divided by servings
     */
    public double fatPerServing() {
        return getFatG() / servings;
    }

    /**
     * Returns the cost of one serving.
     *
     * @return total cost divided by servings
     */
    public double costPerServing() {
        return getCost() / servings;
    }

    /**
     * Returns the recipe's ingredients in order.
     *
     * @return an unmodifiable, non-empty list
     */
    @Override
    public List<Ingredient> getIngredients() {
        return ingredients;
    }

    /**
     * Returns this recipe as the only recipe beneath itself.
     *
     * @return a one-element list containing this recipe
     */
    @Override
    public List<Recipe> getRecipes() {
        return List.of(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Recipe other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Recipe{id=" + id + ", name=" + name + ", servings=" + servings + "}";
    }
}
