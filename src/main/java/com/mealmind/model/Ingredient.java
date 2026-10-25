package com.mealmind.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An immutable amount of one ingredient, such as "500 g chicken breast".
 *
 * <p><strong>Nutrition and cost are totals for the stated quantity</strong>, not per-100 g or
 * per-unit values. For example, {@code 200 g rice} with {@code calories = 260} means the whole
 * 200 g provides 260 kcal. Because of this, {@link #withQuantity(double)} can scale every total by
 * the same factor, and summing ingredients (later, through the Composite pattern) gives correct
 * totals for a recipe or meal plan.
 *
 * <p>In the Composite pattern this is the Leaf of {@link MealComponent}.
 */
public final class Ingredient implements MealComponent {

    private final String name;
    private final double quantity;
    private final Unit unit;
    private final double calories;
    private final double proteinG;
    private final double carbsG;
    private final double fatG;
    private final double cost;

    /**
     * Creates an ingredient.
     *
     * @param name     the ingredient name; trimmed, must not be blank
     * @param quantity the amount, finite and {@code >= 0}
     * @param unit     the unit {@code quantity} is expressed in
     * @param calories total kcal for this quantity, finite and {@code >= 0}
     * @param proteinG total protein in grams for this quantity, finite and {@code >= 0}
     * @param carbsG   total carbohydrates in grams for this quantity, finite and {@code >= 0}
     * @param fatG     total fat in grams for this quantity, finite and {@code >= 0}
     * @param cost     total cost for this quantity, finite and {@code >= 0}
     * @throws IllegalArgumentException if any argument is null, blank, negative, NaN or infinite
     */
    public Ingredient(String name, double quantity, Unit unit, double calories, double proteinG,
                      double carbsG, double fatG, double cost) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Ingredient unit must not be null");
        }
        this.name = name.trim();
        this.quantity = requireNonNegative(quantity, "quantity");
        this.unit = unit;
        this.calories = requireNonNegative(calories, "calories");
        this.proteinG = requireNonNegative(proteinG, "proteinG");
        this.carbsG = requireNonNegative(carbsG, "carbsG");
        this.fatG = requireNonNegative(fatG, "fatG");
        this.cost = requireNonNegative(cost, "cost");
    }

    private static double requireNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be a finite, non-negative number, got " + value);
        }
        return value;
    }

    /**
     * Returns the name in a canonical form for matching duplicates: lowercase, trimmed, with
     * runs of whitespace collapsed to one space. For example {@code "  Chicken   BREAST "} becomes
     * {@code "chicken breast"}.
     *
     * @return the normalized name
     */
    public String normalizedName() {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Returns a copy with a new quantity, scaling calories, macros and cost by
     * {@code newQty / quantity}. This ingredient is not changed.
     *
     * @param newQty the new quantity, finite and {@code >= 0}
     * @return a new, scaled ingredient
     * @throws IllegalArgumentException if {@code newQty} is negative, NaN or infinite
     * @throws IllegalStateException    if this quantity is 0 and {@code newQty} is not, since no
     *                                  scaling factor can be derived
     */
    public Ingredient withQuantity(double newQty) {
        requireNonNegative(newQty, "quantity");
        if (quantity == 0) {
            if (newQty != 0) {
                throw new IllegalStateException("Cannot scale '" + name + "' from a quantity of 0");
            }
            return new Ingredient(name, 0, unit, calories, proteinG, carbsG, fatG, cost);
        }
        double factor = newQty / quantity;
        return new Ingredient(name, newQty, unit, calories * factor, proteinG * factor,
                carbsG * factor, fatG * factor, cost * factor);
    }

    /**
     * Returns a copy with the quantity expressed in another unit. Nutrition and cost totals are
     * unchanged because the amount of food is the same. This ingredient is not changed.
     *
     * @param target the unit to convert to
     * @return a new ingredient in {@code target} units
     * @throws IncompatibleUnitException if {@code target} measures a different dimension
     */
    public Ingredient convertTo(Unit target) {
        double converted = UnitConverter.convert(quantity, unit, target);
        return new Ingredient(name, converted, target, calories, proteinG, carbsG, fatG, cost);
    }

    /**
     * Returns the trimmed display name.
     *
     * @return the name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the amount of this ingredient.
     *
     * @return the quantity, in {@link #getUnit()} units
     */
    public double getQuantity() {
        return quantity;
    }

    /**
     * Returns the unit the quantity is expressed in.
     *
     * @return the unit
     */
    public Unit getUnit() {
        return unit;
    }

    /**
     * Returns the total kcal for this quantity.
     *
     * @return the calories
     */
    @Override
    public double getCalories() {
        return calories;
    }

    /**
     * Returns the total protein for this quantity.
     *
     * @return the protein in grams
     */
    @Override
    public double getProteinG() {
        return proteinG;
    }

    /**
     * Returns the total carbohydrates for this quantity.
     *
     * @return the carbohydrates in grams
     */
    @Override
    public double getCarbsG() {
        return carbsG;
    }

    /**
     * Returns the total fat for this quantity.
     *
     * @return the fat in grams
     */
    @Override
    public double getFatG() {
        return fatG;
    }

    /**
     * Returns the total cost for this quantity.
     *
     * @return the cost
     */
    @Override
    public double getCost() {
        return cost;
    }

    /**
     * Returns this ingredient as the only leaf beneath itself.
     *
     * @return a one-element list containing this ingredient
     */
    @Override
    public List<Ingredient> getIngredients() {
        return List.of(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ingredient other)) {
            return false;
        }
        return name.equals(other.name)
                && unit == other.unit
                && Double.compare(quantity, other.quantity) == 0
                && Double.compare(calories, other.calories) == 0
                && Double.compare(proteinG, other.proteinG) == 0
                && Double.compare(carbsG, other.carbsG) == 0
                && Double.compare(fatG, other.fatG) == 0
                && Double.compare(cost, other.cost) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, quantity, unit, calories, proteinG, carbsG, fatG, cost);
    }

    @Override
    public String toString() {
        return "Ingredient{" + quantity + " " + unit.getSymbol() + " " + name
                + ", calories=" + calories
                + ", proteinG=" + proteinG
                + ", carbsG=" + carbsG
                + ", fatG=" + fatG
                + ", cost=" + cost
                + "}";
    }
}
