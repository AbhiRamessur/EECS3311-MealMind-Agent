package com.mealmind.pantry;

import com.mealmind.model.Names;
import com.mealmind.model.Unit;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable stock entry in the {@link Pantry}, such as "500 g chicken breast, expires 2026-10-10".
 */
public final class PantryItem {

    private final String name;
    private final double quantity;
    private final Unit unit;
    private final Optional<LocalDate> expiry;

    /**
     * Creates a pantry item with an optional expiry date.
     *
     * @param name     the display name; trimmed, must not be blank
     * @param quantity the amount in stock, finite and strictly greater than 0 (UC02 rejects {@code <= 0})
     * @param unit     the unit {@code quantity} is expressed in
     * @param expiry   the expiry date, or {@code null} if the item does not expire
     * @throws IllegalArgumentException if {@code name} is blank, {@code unit} is null or
     *                                  {@code quantity} is not a finite positive number
     */
    public PantryItem(String name, double quantity, Unit unit, LocalDate expiry) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Pantry item name must not be blank");
        }
        if (!Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("Pantry quantity must be a finite number greater than 0, got " + quantity);
        }
        if (unit == null) {
            throw new IllegalArgumentException("Pantry item unit must not be null");
        }
        this.name = name.trim();
        this.quantity = quantity;
        this.unit = unit;
        this.expiry = Optional.ofNullable(expiry);
    }

    /**
     * Creates a pantry item that does not expire.
     *
     * @param name     the display name; trimmed, must not be blank
     * @param quantity the amount in stock, finite and strictly greater than 0
     * @param unit     the unit {@code quantity} is expressed in
     * @throws IllegalArgumentException if any argument is invalid
     */
    public PantryItem(String name, double quantity, Unit unit) {
        this(name, quantity, unit, null);
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
     * Returns the name in canonical form, used as the pantry key.
     *
     * @return the normalized name
     * @see Names#normalize(String)
     */
    public String normalizedName() {
        return Names.normalize(name);
    }

    /**
     * Returns the amount in stock.
     *
     * @return the quantity, in {@link #getUnit()} units, always greater than 0
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
     * Returns the expiry date, if the item has one.
     *
     * @return the expiry date, or empty if the item does not expire
     */
    public Optional<LocalDate> getExpiry() {
        return expiry;
    }

    /**
     * Returns a copy with a different quantity and the same name, unit and expiry.
     *
     * @param newQuantity the new quantity, finite and strictly greater than 0
     * @return a new item
     * @throws IllegalArgumentException if {@code newQuantity} is invalid
     */
    public PantryItem withQuantity(double newQuantity) {
        return new PantryItem(name, newQuantity, unit, expiry.orElse(null));
    }

    /**
     * Returns the number of days from {@code today} until the expiry date.
     *
     * @param today the current date
     * @return days until expiry: 0 on the expiry day, negative once expired
     * @throws IllegalStateException if the item has no expiry date
     * @throws NullPointerException  if {@code today} is null
     */
    public long daysUntilExpiry(LocalDate today) {
        Objects.requireNonNull(today, "today");
        LocalDate date = expiry.orElseThrow(
                () -> new IllegalStateException("Pantry item '" + name + "' has no expiry date"));
        return ChronoUnit.DAYS.between(today, date);
    }

    /**
     * Returns whether the expiry date is before {@code today}. An item is still usable on its expiry day.
     *
     * @param today the current date
     * @return {@code true} if expired; {@code false} if not, or if the item has no expiry date
     * @throws NullPointerException if {@code today} is null
     */
    public boolean isExpired(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return expiry.map(date -> date.isBefore(today)).orElse(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PantryItem other)) {
            return false;
        }
        return name.equals(other.name)
                && Double.compare(quantity, other.quantity) == 0
                && unit == other.unit
                && expiry.equals(other.expiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, quantity, unit, expiry);
    }

    @Override
    public String toString() {
        return "PantryItem{" + quantity + " " + unit.getSymbol() + " " + name
                + expiry.map(date -> ", expires " + date).orElse("") + "}";
    }
}
