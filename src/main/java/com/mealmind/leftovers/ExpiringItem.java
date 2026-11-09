package com.mealmind.leftovers;

import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A pantry item that expires soon, with how urgent it is.
 *
 * @param name            the display name, e.g. "Red lentils"
 * @param normalizedName  the name as {@code Names.normalize} gives it
 * @param daysUntilExpiry days until the expiry date: 0 on the day, negative once expired
 * @param expired         whether the expiry date has passed
 * @param weight          urgency from 1 to 4 (4 = expired or due today)
 */
public record ExpiringItem(String name, String normalizedName, long daysUntilExpiry, boolean expired, int weight) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException     if a name is null
     * @throws IllegalArgumentException if {@code weight} is not 1 to 4
     */
    public ExpiringItem {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(normalizedName, "normalizedName");
        if (weight < 1 || weight > 4) {
            throw new IllegalArgumentException("Weight must be 1 to 4, got " + weight);
        }
    }

    /**
     * Describes a pantry item that has an expiry date.
     *
     * @param item  the pantry item
     * @param today the current date
     * @return the expiring item
     * @throws IllegalArgumentException if the item has no expiry date
     * @throws NullPointerException     if an argument is null
     */
    public static ExpiringItem of(PantryItem item, LocalDate today) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(today, "today");
        if (item.getExpiry().isEmpty()) {
            throw new IllegalArgumentException("Pantry item '" + item.getName() + "' has no expiry date");
        }
        long days = item.daysUntilExpiry(today);
        return new ExpiringItem(item.getName(), item.normalizedName(), days, item.isExpired(today), weightFor(days));
    }

    /** 4 when expired or due today, 3 when due in 1 day, 2 in 2 days, 1 otherwise. */
    private static int weightFor(long days) {
        if (days <= 0) {
            return 4;
        }
        if (days == 1) {
            return 3;
        }
        return days == 2 ? 2 : 1;
    }

    /**
     * Returns the name with its expiry, e.g. "Red lentils (expires in 1 day)".
     *
     * @return the label
     */
    public String label() {
        return name + " (" + when() + ")";
    }

    private String when() {
        if (daysUntilExpiry == 0) {
            return "expires today";
        }
        if (daysUntilExpiry > 0) {
            return "expires in " + days(daysUntilExpiry);
        }
        return "expired " + days(-daysUntilExpiry) + " ago";
    }

    private static String days(long count) {
        return count + (count == 1 ? " day" : " days");
    }
}
