package com.mealmind.gui.logic;

import com.mealmind.recommend.Recommendation;
import com.mealmind.recommend.RecommendationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a {@link RecommendationResult} into table rows and notes, in the result's order.
 */
public final class RecommendRows {

    private RecommendRows() {
    }

    /**
     * Returns one row per recommendation, ranked from "1".
     *
     * @param result the recommendations
     * @return the rows
     * @throws NullPointerException if {@code result} is null
     */
    public static List<RecommendRow> of(RecommendationResult result) {
        Objects.requireNonNull(result, "result");
        List<RecommendRow> rows = new ArrayList<>();
        for (int i = 0; i < result.items().size(); i++) {
            Recommendation item = result.items().get(i);
            rows.add(new RecommendRow(String.valueOf(i + 1), item.recipe().getId(), item.recipe().getName(), item.reason()));
        }
        return List.copyOf(rows);
    }

    /**
     * Returns the result's notes.
     *
     * @param result the recommendations
     * @return the notes, unmodifiable
     */
    public static List<String> notes(RecommendationResult result) {
        return Objects.requireNonNull(result, "result").notes();
    }
}
