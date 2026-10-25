package com.mealmind.pantry;

import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Names;
import com.mealmind.model.ObservableSubject;
import com.mealmind.model.Unit;
import com.mealmind.model.UnitConverter;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The user's kitchen inventory (F02 / UC02), keyed by normalized ingredient name, so
 * {@code "Chicken Breast"} and {@code " chicken  breast "} are the same entry.
 *
 * <p>A ConcreteSubject in the Observer pattern: observers are notified once after every
 * successful change and never after a failed or no-op one.
 *
 * <p><strong>Merge policy</strong> for {@link #addItem}: an item whose name already exists is
 * merged into the existing entry. Its quantity is converted into the existing unit and added; the
 * existing display name and unit are kept; and the earlier of the two expiry dates is kept (the
 * conservative choice), or whichever one exists. Unit normalization therefore happens inside
 * {@code addItem}; there is no separate public {@code normalizeUnits()} method.
 *
 * <p>Methods that change the pantry return the previous state so that the later Command classes
 * can undo them, and {@link #replaceItem} sets an entry exactly, for use by undo.
 *
 * <p>Dates are always passed in; the pantry never reads the system clock.
 */
public final class Pantry extends ObservableSubject {

    private static final Comparator<PantryItem> BY_NAME = Comparator.comparing(PantryItem::normalizedName);

    private final Map<String, PantryItem> items = new HashMap<>();

    /**
     * Adds stock, merging with an existing entry of the same normalized name (see class Javadoc).
     *
     * @param incoming the stock to add
     * @return the entry's state before this call, or empty if the item was new
     * @throws IncompatibleUnitException if an entry exists with a unit of a different dimension;
     *                                   the pantry is then unchanged and no one is notified
     * @throws NullPointerException      if {@code incoming} is null
     */
    public Optional<PantryItem> addItem(PantryItem incoming) {
        Objects.requireNonNull(incoming, "incoming");
        String key = incoming.normalizedName();
        PantryItem existing = items.get(key);
        if (existing == null) {
            items.put(key, incoming);
            notifyObservers("Added " + incoming.getName());
            return Optional.empty();
        }
        // Convert first: if the units are incompatible this throws before anything is changed.
        double added = UnitConverter.convert(incoming.getQuantity(), incoming.getUnit(), existing.getUnit());
        PantryItem merged = new PantryItem(existing.getName(), existing.getQuantity() + added,
                existing.getUnit(), earlier(existing.getExpiry(), incoming.getExpiry()));
        items.put(key, merged);
        notifyObservers("Merged " + incoming.getName() + " into " + existing.getName());
        return Optional.of(existing);
    }

    private static LocalDate earlier(Optional<LocalDate> a, Optional<LocalDate> b) {
        if (a.isPresent() && b.isPresent()) {
            return a.get().isBefore(b.get()) ? a.get() : b.get();
        }
        return a.or(() -> b).orElse(null);
    }

    /**
     * Removes the entry with the given name.
     *
     * @param name the item name, matched after normalization
     * @return the removed item, or empty if there was none (in which case no one is notified)
     * @throws NullPointerException if {@code name} is null
     */
    public Optional<PantryItem> removeItem(String name) {
        Optional<PantryItem> removed = Optional.ofNullable(items.remove(Names.normalize(name)));
        removed.ifPresent(item -> notifyObservers("Removed " + item.getName()));
        return removed;
    }

    /**
     * Sets the entry for the item's normalized name to exactly {@code item}, without merging.
     * Used to restore a previous state when undoing.
     *
     * @param item the entry to store
     * @throws NullPointerException if {@code item} is null
     */
    public void replaceItem(PantryItem item) {
        Objects.requireNonNull(item, "item");
        items.put(item.normalizedName(), item);
        notifyObservers("Replaced " + item.getName());
    }

    /**
     * Looks up an entry by name.
     *
     * @param name the item name, matched after normalization
     * @return the entry, or empty if there is none
     * @throws NullPointerException if {@code name} is null
     */
    public Optional<PantryItem> getItem(String name) {
        return Optional.ofNullable(items.get(Names.normalize(name)));
    }

    /**
     * Returns all entries.
     *
     * @return an unmodifiable list sorted by normalized name
     */
    public List<PantryItem> getItems() {
        return items.values().stream().sorted(BY_NAME).toList();
    }

    /**
     * Returns the number of entries.
     *
     * @return the size
     */
    public int size() {
        return items.size();
    }

    /**
     * Returns whether the pantry has no entries.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Returns how much of an item is in stock, expressed in the requested unit.
     *
     * @param name the item name, matched after normalization
     * @param unit the unit to express the quantity in
     * @return the converted quantity, or 0 if the item is not in the pantry
     * @throws IncompatibleUnitException if the stored unit has a different dimension than {@code unit}
     * @throws NullPointerException      if an argument is null
     */
    public double availableQuantity(String name, Unit unit) {
        Objects.requireNonNull(unit, "unit");
        PantryItem item = items.get(Names.normalize(name));
        if (item == null) {
            return 0;
        }
        return UnitConverter.convert(item.getQuantity(), item.getUnit(), unit);
    }

    /**
     * Returns items that expire on or before {@code today + withinDays}, including items that have
     * already expired. Items without an expiry date are never included.
     *
     * @param today      the current date
     * @param withinDays how many days ahead to look, {@code >= 0}
     * @return an unmodifiable list sorted by expiry date, then normalized name
     * @throws IllegalArgumentException if {@code withinDays} is negative
     * @throws NullPointerException     if {@code today} is null
     */
    public List<PantryItem> getExpiringItems(LocalDate today, int withinDays) {
        Objects.requireNonNull(today, "today");
        if (withinDays < 0) {
            throw new IllegalArgumentException("withinDays must not be negative, got " + withinDays);
        }
        LocalDate cutoff = today.plusDays(withinDays);
        return items.values().stream()
                .filter(item -> item.getExpiry().map(date -> !date.isAfter(cutoff)).orElse(false))
                .sorted(Comparator.comparing((PantryItem item) -> item.getExpiry().orElseThrow()).thenComparing(BY_NAME))
                .toList();
    }
}
