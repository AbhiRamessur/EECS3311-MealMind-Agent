package com.mealmind.gui.logic;

/**
 * How urgent a pantry item's expiry is; the Pantry tab colors rows by it.
 */
public enum ExpiryLevel {
    /** No expiry date, or more than 3 days left. */
    NONE,
    /** Expires today or within 3 days. */
    SOON,
    /** Already expired. */
    EXPIRED
}
