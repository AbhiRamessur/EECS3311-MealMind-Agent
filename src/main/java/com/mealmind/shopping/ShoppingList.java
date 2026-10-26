package com.mealmind.shopping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable shopping list: the items still to buy, sorted by aisle (in {@link Aisle} order), then
 * name, then unit, plus any advisory warnings for the user. It is built in one pass by
 * {@link ShoppingListGenerator} rather than filled item by item.
 */
public final class ShoppingList {

    private static final Comparator<ShoppingItem> ORDER = Comparator.comparing(ShoppingItem::getAisle)
            .thenComparing(ShoppingItem::normalizedName)
            .thenComparing(ShoppingItem::getUnit);

    private static final ShoppingList EMPTY = new ShoppingList(List.of(), List.of());

    private final List<ShoppingItem> items;
    private final List<String> warnings;
    private final Map<Aisle, List<ShoppingItem>> byAisle;

    /**
     * Creates a list. Both lists are copied; items are sorted.
     *
     * @param items    the items to buy, in any order
     * @param warnings advisory messages, in display order
     * @throws NullPointerException if a list or one of its elements is null
     */
    public ShoppingList(List<ShoppingItem> items, List<String> warnings) {
        this.items = items.stream().sorted(ORDER).toList();
        this.warnings = List.copyOf(warnings);
        Map<Aisle, List<ShoppingItem>> grouped = new EnumMap<>(Aisle.class);
        for (ShoppingItem item : this.items) {
            grouped.computeIfAbsent(item.getAisle(), aisle -> new ArrayList<>()).add(item);
        }
        grouped.replaceAll((aisle, list) -> List.copyOf(list));
        this.byAisle = Collections.unmodifiableMap(grouped);
    }

    /**
     * Returns a list with no items and no warnings.
     *
     * @return the empty list
     */
    public static ShoppingList empty() {
        return EMPTY;
    }

    /**
     * Returns the items to buy.
     *
     * @return an unmodifiable list sorted by aisle, name, then unit
     */
    public List<ShoppingItem> getItems() {
        return items;
    }

    /**
     * Returns advisory warnings, such as an empty pantry.
     *
     * @return an unmodifiable list, possibly empty
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Returns the number of items.
     *
     * @return the size
     */
    public int size() {
        return items.size();
    }

    /**
     * Returns whether there is nothing to buy.
     *
     * @return {@code true} if there are no items
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Returns the sum of all items' estimated costs.
     *
     * @return the total, 0 for an empty list
     */
    public double getTotalEstimatedCost() {
        return items.stream().mapToDouble(ShoppingItem::getEstimatedCost).sum();
    }

    /**
     * Returns the items grouped by aisle.
     *
     * @return an unmodifiable map in {@link Aisle} order containing only aisles that have items
     */
    public Map<Aisle, List<ShoppingItem>> byAisle() {
        return byAisle;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof ShoppingList other && items.equals(other.items) && warnings.equals(other.warnings));
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, warnings);
    }

    @Override
    public String toString() {
        return "ShoppingList{items=" + items.size() + ", warnings=" + warnings.size()
                + ", total=" + getTotalEstimatedCost() + "}";
    }
}
