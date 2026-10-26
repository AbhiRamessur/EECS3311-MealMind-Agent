package com.mealmind.shopping;

import com.mealmind.model.Names;
import com.mealmind.model.Unit;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of a {@link ShoppingList}: how much of an ingredient still has to be bought, its
 * estimated cost, its aisle and an optional explanatory note.
 */
public final class ShoppingItem {

    private final String name;
    private final double quantity;
    private final Unit unit;
    private final double estimatedCost;
    private final Aisle aisle;
    private final String note;

    /**
     * Creates a shopping item.
     *
     * @param name          display name; trimmed, must not be blank
     * @param quantity      amount to buy, finite and greater than 0
     * @param unit          the unit {@code quantity} is expressed in
     * @param estimatedCost estimated cost of {@code quantity}, finite and {@code >= 0}
     * @param aisle         the store section
     * @param note          an explanation shown with the item, or {@code null} (blank counts as none)
     * @throws IllegalArgumentException if any required argument is null, blank or out of range
     */
    public ShoppingItem(String name, double quantity, Unit unit, double estimatedCost, Aisle aisle, String note) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shopping item name must not be blank");
        }
        if (!Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("Shopping quantity must be a finite number greater than 0, got " + quantity);
        }
        if (unit == null) {
            throw new IllegalArgumentException("Shopping item unit must not be null");
        }
        if (!Double.isFinite(estimatedCost) || estimatedCost < 0) {
            throw new IllegalArgumentException("Estimated cost must be a finite, non-negative number, got " + estimatedCost);
        }
        if (aisle == null) {
            throw new IllegalArgumentException("Shopping item aisle must not be null");
        }
        this.name = name.trim();
        this.quantity = quantity;
        this.unit = unit;
        this.estimatedCost = estimatedCost;
        this.aisle = aisle;
        this.note = (note == null || note.isBlank()) ? null : note.trim();
    }

    /**
     * Returns the trimmed display name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the name in canonical form.
     *
     * @return the normalized name
     */
    public String normalizedName() {
        return Names.normalize(name);
    }

    /**
     * Returns the amount still to buy.
     *
     * @return the quantity, in {@link #getUnit()} units
     */
    public double getQuantity() {
        return quantity;
    }

    /**
     * Returns the unit of the quantity.
     *
     * @return the unit
     */
    public Unit getUnit() {
        return unit;
    }

    /**
     * Returns the estimated cost of the quantity to buy.
     *
     * @return the cost
     */
    public double getEstimatedCost() {
        return estimatedCost;
    }

    /**
     * Returns the store section.
     *
     * @return the aisle
     */
    public Aisle getAisle() {
        return aisle;
    }

    /**
     * Returns the explanatory note, if any.
     *
     * @return the note, or empty
     */
    public Optional<String> getNote() {
        return Optional.ofNullable(note);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShoppingItem other)) {
            return false;
        }
        return name.equals(other.name)
                && Double.compare(quantity, other.quantity) == 0
                && unit == other.unit
                && Double.compare(estimatedCost, other.estimatedCost) == 0
                && aisle == other.aisle
                && Objects.equals(note, other.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, quantity, unit, estimatedCost, aisle, note);
    }

    @Override
    public String toString() {
        return "ShoppingItem{" + quantity + " " + unit.getSymbol() + " " + name
                + ", cost=" + estimatedCost + ", aisle=" + aisle
                + (note == null ? "" : ", note=" + note) + "}";
    }
}
