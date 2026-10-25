package com.mealmind.model;

import java.util.List;
import java.util.Locale;

/**
 * A unit of measure for ingredient quantities.
 *
 * <p>Each unit belongs to a {@link Dimension} and has a factor that converts one of it into the
 * dimension's base unit: grams for {@link Dimension#MASS}, milliliters for {@link Dimension#VOLUME}
 * and pieces for {@link Dimension#COUNT}.
 */
public enum Unit {
    GRAM(Dimension.MASS, 1.0, "g", "gram", "grams"),
    KILOGRAM(Dimension.MASS, 1000.0, "kg", "kilogram", "kilograms"),
    MILLILITER(Dimension.VOLUME, 1.0, "ml", "milliliter", "milliliters", "millilitre", "millilitres"),
    LITER(Dimension.VOLUME, 1000.0, "l", "liter", "liters", "litre", "litres"),
    TEASPOON(Dimension.VOLUME, 4.92892, "tsp", "teaspoon", "teaspoons"),
    TABLESPOON(Dimension.VOLUME, 14.7868, "tbsp", "tablespoon", "tablespoons"),
    CUP(Dimension.VOLUME, 236.588, "cup", "cups"),
    COUNT(Dimension.COUNT, 1.0, "pc", "pcs", "piece", "pieces", "count");

    private final Dimension dimension;
    private final double factorToBase;
    private final String symbol;
    private final List<String> names;

    Unit(Dimension dimension, double factorToBase, String symbol, String... names) {
        this.dimension = dimension;
        this.factorToBase = factorToBase;
        this.symbol = symbol;
        this.names = List.of(names);
    }

    /**
     * Returns the dimension this unit measures.
     *
     * @return the dimension
     */
    public Dimension getDimension() {
        return dimension;
    }

    /**
     * Returns how many base units (g, ml or pieces) one of this unit equals.
     *
     * @return the conversion factor to the base unit, always positive
     */
    public double getFactorToBase() {
        return factorToBase;
    }

    /**
     * Returns the short display symbol, e.g. {@code "g"} or {@code "tbsp"}.
     *
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Parses a unit from its symbol or full name, ignoring case and surrounding whitespace.
     * For example {@code "g"}, {@code "Grams"}, {@code " KG "} and {@code "cups"} are all accepted.
     *
     * @param text the text to parse
     * @return the matching unit
     * @throws IllegalArgumentException if {@code text} is null, blank or not a known unit
     */
    public static Unit parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Unit must not be blank");
        }
        String key = text.trim().toLowerCase(Locale.ROOT);
        for (Unit unit : values()) {
            if (unit.symbol.equals(key) || unit.names.contains(key)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown unit '" + text.trim()
                + "'. Use one of: g, kg, ml, l, tsp, tbsp, cup, pc");
    }
}
