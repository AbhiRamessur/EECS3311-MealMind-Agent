package com.mealmind.model;

import java.util.List;

/**
 * An immutable meal eaten in one slot of a day, such as "Dinner". A Composite in the
 * {@link MealComponent} pattern whose children are usually {@link Recipe}s but may include loose
 * {@link Ingredient}s (e.g. a recipe plus an apple).
 *
 * <p>Totals are the sums over the components.
 */
public final class Meal implements MealComponent {

    private final MealType type;
    private final String label;
    private final List<MealComponent> components;

    /**
     * Creates a meal. The component list is copied, so later changes to it have no effect.
     *
     * @param type       the slot this meal is for
     * @param label      optional display name; when null or blank, the type's display name is used
     * @param components the recipes and/or ingredients in this meal; must not be empty
     * @throws IllegalArgumentException if {@code type} is null or {@code components} is null or empty
     */
    public Meal(MealType type, String label, List<? extends MealComponent> components) {
        if (type == null) {
            throw new IllegalArgumentException("Meal type must not be null");
        }
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Meal must have at least one component");
        }
        this.type = type;
        this.label = (label == null || label.isBlank()) ? type.getDisplayName() : label.trim();
        this.components = List.copyOf(components);
    }

    /**
     * Returns the slot this meal is for.
     *
     * @return the meal type
     */
    public MealType getType() {
        return type;
    }

    /**
     * Returns the meal's components in order.
     *
     * @return an unmodifiable, non-empty list
     */
    public List<MealComponent> getComponents() {
        return components;
    }

    /**
     * Returns the label, or the meal type's display name if no label was given.
     *
     * @return the display name
     */
    @Override
    public String getName() {
        return label;
    }

    @Override
    public double getCalories() {
        return MealComponents.sum(components, MealComponent::getCalories);
    }

    @Override
    public double getProteinG() {
        return MealComponents.sum(components, MealComponent::getProteinG);
    }

    @Override
    public double getCarbsG() {
        return MealComponents.sum(components, MealComponent::getCarbsG);
    }

    @Override
    public double getFatG() {
        return MealComponents.sum(components, MealComponent::getFatG);
    }

    @Override
    public double getCost() {
        return MealComponents.sum(components, MealComponent::getCost);
    }

    @Override
    public List<Ingredient> getIngredients() {
        return MealComponents.ingredientsOf(components);
    }

    @Override
    public List<Recipe> getRecipes() {
        return MealComponents.recipesOf(components);
    }

    @Override
    public String toString() {
        return "Meal{type=" + type + ", label=" + label + ", components=" + components.size() + "}";
    }
}
