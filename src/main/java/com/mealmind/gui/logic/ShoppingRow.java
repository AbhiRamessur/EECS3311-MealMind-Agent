package com.mealmind.gui.logic;

/**
 * One row of the Shopping List tab.
 *
 * @param key          a stable key for check-off marks ({@link ShoppingRows#key})
 * @param aisle        the store section, e.g. "Produce"
 * @param name         the item name
 * @param quantityText the amount with its unit, e.g. "1.5 kg"
 * @param costText     e.g. "$1.25"
 * @param note         the item's note, or ""
 * @param checked      whether the user checked it off in this session
 */
public record ShoppingRow(String key, String aisle, String name, String quantityText, String costText, String note,
                          boolean checked) {
}
