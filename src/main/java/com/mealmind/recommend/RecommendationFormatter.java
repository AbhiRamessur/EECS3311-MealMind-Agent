package com.mealmind.recommend;

import java.util.List;
import java.util.Objects;

/**
 * Formats a {@link RecommendationResult} as text: notes first ("Note: ..."), then numbered lines
 * such as {@code "1. R103 Red Lentil Soup - Covers 50% of your pantry; 40 min"}.
 */
public final class RecommendationFormatter {

    private RecommendationFormatter() {
    }

    /**
     * Formats a result.
     *
     * @param result the result
     * @return the text with {@code "\n"} line endings, or "No recommendations." when there are no items
     * @throws NullPointerException if {@code result} is null
     */
    public static String toText(RecommendationResult result) {
        Objects.requireNonNull(result, "result");
        StringBuilder text = new StringBuilder();
        result.notes().forEach(note -> text.append("Note: ").append(note).append('\n'));
        List<Recommendation> items = result.items();
        if (items.isEmpty()) {
            return text.append("No recommendations.\n").toString();
        }
        for (int i = 0; i < items.size(); i++) {
            Recommendation item = items.get(i);
            text.append(i + 1).append(". ").append(item.recipe().getId()).append(' ').append(item.recipe().getName())
                    .append(" - ").append(item.reason()).append('\n');
        }
        return text.toString();
    }
}
