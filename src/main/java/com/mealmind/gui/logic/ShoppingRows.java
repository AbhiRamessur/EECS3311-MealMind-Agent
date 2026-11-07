package com.mealmind.gui.logic;

import com.mealmind.cli.CliFormat;
import com.mealmind.shopping.Aisle;
import com.mealmind.shopping.ShoppingItem;
import com.mealmind.shopping.ShoppingList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the Shopping List tab's rows in the list's own order (aisle, then name), with check-off flags.
 * Amounts use the CLI's display rule ({@link CliFormat#amount}: 1000 g or more as kg, 1000 ml or more as l).
 */
public final class ShoppingRows {

    private ShoppingRows() {
    }

    /**
     * Returns the rows.
     *
     * @param list        the shopping list
     * @param checkedKeys keys of checked items (unknown keys are ignored)
     * @return the rows
     * @throws NullPointerException if an argument is null
     */
    public static List<ShoppingRow> of(ShoppingList list, Set<String> checkedKeys) {
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(checkedKeys, "checkedKeys");
        List<ShoppingRow> rows = new ArrayList<>();
        for (Map.Entry<Aisle, List<ShoppingItem>> section : list.byAisle().entrySet()) {
            for (ShoppingItem item : section.getValue()) {
                String key = key(item);
                rows.add(new ShoppingRow(key, section.getKey().getDisplayName(), item.getName(),
                        CliFormat.amount(item.getQuantity(), item.getUnit()),
                        "$" + String.format(Locale.ROOT, "%.2f", item.getEstimatedCost()), item.getNote().orElse(""),
                        checkedKeys.contains(key)));
            }
        }
        return List.copyOf(rows);
    }

    /**
     * Returns the check-off key of an item: its normalized name and unit symbol, e.g. "red lentils|g".
     *
     * @param item the item
     * @return the key
     */
    public static String key(ShoppingItem item) {
        return item.normalizedName() + "|" + item.getUnit().getSymbol();
    }

    /**
     * Keeps only the keys of items that are still on the list.
     *
     * @param list the current list
     * @param keys the checked keys
     * @return a new set
     */
    public static Set<String> retainKnown(ShoppingList list, Set<String> keys) {
        Set<String> known = list.getItems().stream().map(ShoppingRows::key).collect(Collectors.toSet());
        return keys.stream().filter(known::contains).collect(Collectors.toSet());
    }

    /**
     * Returns the total line.
     *
     * @param list the shopping list
     * @return e.g. "Estimated total: $12.34"
     */
    public static String totalText(ShoppingList list) {
        return "Estimated total: $" + String.format(Locale.ROOT, "%.2f", list.getTotalEstimatedCost());
    }

    /**
     * Returns the list's warnings.
     *
     * @param list the shopping list
     * @return the warnings, unmodifiable
     */
    public static List<String> warnings(ShoppingList list) {
        return list.getWarnings();
    }
}
