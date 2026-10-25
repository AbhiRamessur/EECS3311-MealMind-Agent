package com.mealmind.model;

/**
 * The physical quantity a {@link Unit} measures. Quantities can only be converted between units
 * of the same dimension.
 */
public enum Dimension {
    /** Weight; base unit is the gram. */
    MASS,
    /** Volume; base unit is the milliliter. */
    VOLUME,
    /** Whole items (e.g. 3 eggs); base unit is one piece. */
    COUNT
}
