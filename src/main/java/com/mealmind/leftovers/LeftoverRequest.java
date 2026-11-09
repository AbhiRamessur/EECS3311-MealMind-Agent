package com.mealmind.leftovers;

/**
 * What the user asked for in {@code plan leftovers}.
 *
 * @param withinDays look for items expiring within this many days, 1 to {@value #MAX_DAYS}
 * @param meals      propose at most this many meals, 1 to {@value #MAX_MEALS}
 * @param overwrite  whether filled slots may be replaced (after all empty slots)
 */
public record LeftoverRequest(int withinDays, int meals, boolean overwrite) {

    /** The largest look-ahead in days. */
    public static final int MAX_DAYS = 14;
    /** The largest number of proposed meals. */
    public static final int MAX_MEALS = 6;

    /**
     * Validates the bounds.
     *
     * @throws IllegalArgumentException if a number is out of range
     */
    public LeftoverRequest {
        if (withinDays < 1 || withinDays > MAX_DAYS) {
            throw new IllegalArgumentException("Days must be from 1 to " + MAX_DAYS + ", got " + withinDays);
        }
        if (meals < 1 || meals > MAX_MEALS) {
            throw new IllegalArgumentException("Meals must be from 1 to " + MAX_MEALS + ", got " + meals);
        }
    }

    /**
     * Returns the defaults: 3 days, 3 meals, no overwrite.
     *
     * @return the default request
     */
    public static LeftoverRequest defaults() {
        return new LeftoverRequest(3, 3, false);
    }
}
