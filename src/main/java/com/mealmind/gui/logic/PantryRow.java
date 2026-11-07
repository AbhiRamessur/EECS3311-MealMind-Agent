package com.mealmind.gui.logic;

/**
 * One row of the Pantry tab's table, as display texts.
 *
 * @param name         the item name
 * @param quantityText the quantity as the CLI shows it, e.g. "1.5"
 * @param unitSymbol   the shown unit, e.g. "kg"
 * @param expiryText   the ISO expiry date, or "-"
 * @param statusText   "EXPIRED", "expires today", "expires in N days" (up to 3), or empty
 * @param level        the urgency
 */
public record PantryRow(String name, String quantityText, String unitSymbol, String expiryText, String statusText,
                        ExpiryLevel level) {
}
