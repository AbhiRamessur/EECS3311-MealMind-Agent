package com.mealmind.model;

import java.util.Objects;

/**
 * Converts quantities between {@link Unit}s of the same {@link Dimension}.
 */
public final class UnitConverter {

    private UnitConverter() {
    }

    /**
     * Converts a quantity from one unit to another.
     *
     * @param qty  the quantity, finite and non-negative
     * @param from the unit {@code qty} is expressed in
     * @param to   the unit to convert to
     * @return the quantity expressed in {@code to}; unchanged if both units are the same
     * @throws IllegalArgumentException  if {@code qty} is negative, NaN or infinite
     * @throws IncompatibleUnitException if the units measure different dimensions
     */
    public static double convert(double qty, Unit from, Unit to) {
        Objects.requireNonNull(to, "to");
        double base = toBase(qty, from);
        if (from == to) {
            return qty;
        }
        if (!areCompatible(from, to)) {
            throw new IncompatibleUnitException(from, to);
        }
        return base / to.getFactorToBase();
    }

    /**
     * Returns whether quantities in the two units can be converted into each other.
     *
     * @param a the first unit
     * @param b the second unit
     * @return {@code true} if both units have the same dimension
     */
    public static boolean areCompatible(Unit a, Unit b) {
        return a.getDimension() == b.getDimension();
    }

    /**
     * Converts a quantity to its dimension's base unit (grams, milliliters or pieces).
     *
     * @param qty the quantity, finite and non-negative
     * @param u   the unit {@code qty} is expressed in
     * @return the quantity in base units
     * @throws IllegalArgumentException if {@code qty} is negative, NaN or infinite
     */
    public static double toBase(double qty, Unit u) {
        Objects.requireNonNull(u, "unit");
        if (!Double.isFinite(qty) || qty < 0) {
            throw new IllegalArgumentException("Quantity must be a finite, non-negative number, got " + qty);
        }
        return qty * u.getFactorToBase();
    }
}
