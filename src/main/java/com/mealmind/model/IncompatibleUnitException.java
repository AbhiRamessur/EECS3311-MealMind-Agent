package com.mealmind.model;

/**
 * Thrown when a quantity is converted between units of different dimensions, such as grams to
 * liters (which would need a density) or cups to pieces.
 */
// Unchecked because it signals invalid input that callers validate up front with
// UnitConverter.areCompatible(); forcing every conversion call site to catch it would add noise
// to the pantry, recipe and shopping-list code without making it safer.
public class IncompatibleUnitException extends RuntimeException {

    /**
     * Creates an exception naming both units involved.
     *
     * @param from the unit being converted from
     * @param to   the unit being converted to
     */
    public IncompatibleUnitException(Unit from, Unit to) {
        super("Cannot convert " + from + " (" + from.getDimension() + ") to "
                + to + " (" + to.getDimension() + "): units measure different dimensions");
    }
}
