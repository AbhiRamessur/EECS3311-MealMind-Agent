package com.mealmind.recommend;

/**
 * What to recommend.
 *
 * @param pantryFirst    rank by pantry coverage first (otherwise ratings and favourites come first)
 * @param maxPrepMinutes the longest acceptable preparation time, or null for no limit
 * @param limit          how many recipes to return, 1 to 10
 */
public record RecommendationRequest(boolean pantryFirst, Integer maxPrepMinutes, int limit) {

    /** The largest allowed {@code limit}. */
    public static final int MAX_LIMIT = 10;

    /**
     * Validates the fields.
     *
     * @throws IllegalArgumentException if {@code maxPrepMinutes} is below 1 or {@code limit} is out of range
     */
    public RecommendationRequest {
        if (maxPrepMinutes != null && maxPrepMinutes < 1) {
            throw new IllegalArgumentException("Maximum prep time must be at least 1 minute, got " + maxPrepMinutes);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
    }

    /**
     * Returns the defaults: preferences first, no time limit, 5 results.
     *
     * @return the default request
     */
    public static RecommendationRequest defaults() {
        return new RecommendationRequest(false, null, 5);
    }
}
