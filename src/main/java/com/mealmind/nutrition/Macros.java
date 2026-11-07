package com.mealmind.nutrition;

import com.mealmind.model.MealComponent;
import java.util.Objects;

/**
 * Calories, macros and cost of something.
 *
 * @param calories kcal
 * @param proteinG protein in grams
 * @param carbsG   carbohydrates in grams
 * @param fatG     fat in grams
 * @param cost     cost in dollars
 */
public record Macros(double calories, double proteinG, double carbsG, double fatG, double cost) {

    /** All zeros. */
    public static final Macros ZERO = new Macros(0, 0, 0, 0, 0);

    /**
     * Reads the totals of any meal component. This is the Composite pattern in use: an ingredient, recipe,
     * meal, day or plan all answer the same questions, so no type checks are needed.
     *
     * @param component what to read
     * @return its totals
     * @throws NullPointerException if {@code component} is null
     */
    public static Macros of(MealComponent component) {
        Objects.requireNonNull(component, "component");
        return new Macros(component.getCalories(), component.getProteinG(), component.getCarbsG(),
                component.getFatG(), component.getCost());
    }

    /**
     * Divides every value by {@code divisor}.
     *
     * @param divisor a positive number
     * @return the divided values
     * @throws IllegalArgumentException if {@code divisor < 1}
     */
    public Macros dividedBy(int divisor) {
        if (divisor < 1) {
            throw new IllegalArgumentException("Divisor must be at least 1, got " + divisor);
        }
        return new Macros(calories / divisor, proteinG / divisor, carbsG / divisor, fatG / divisor, cost / divisor);
    }
}
