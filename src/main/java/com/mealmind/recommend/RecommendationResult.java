package com.mealmind.recommend;

import java.util.List;

/**
 * Recommendations in order, and notes about how they were chosen.
 *
 * @param items the recommendations, best first (unmodifiable)
 * @param notes plain-text notes (unmodifiable)
 */
public record RecommendationResult(List<Recommendation> items, List<String> notes) {

    /**
     * Copies the lists.
     *
     * @throws NullPointerException if a list or element is null
     */
    public RecommendationResult {
        items = List.copyOf(items);
        notes = List.copyOf(notes);
    }
}
