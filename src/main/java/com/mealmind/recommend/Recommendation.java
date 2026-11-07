package com.mealmind.recommend;

import com.mealmind.model.Recipe;
import java.util.List;
import java.util.Objects;

/**
 * One recommended recipe.
 *
 * @param recipe          the recipe (always one from the recipe book that passed the safety check)
 * @param coveragePercent how much of it the pantry covers, 0 to 100
 * @param missing         uncovered ingredient names, in recipe order (unmodifiable)
 * @param favorite        whether the user marked it as a favourite
 * @param rating          the user's rating, or null
 * @param reason          why it is recommended; non-blank
 */
public record Recommendation(Recipe recipe, int coveragePercent, List<String> missing, boolean favorite,
                             Integer rating, String reason) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException     if {@code recipe}, {@code missing} or {@code reason} is null
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public Recommendation {
        Objects.requireNonNull(recipe, "recipe");
        missing = List.copyOf(missing);
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank");
        }
    }

    /**
     * Returns a copy with another reason.
     *
     * @param newReason the new reason, non-blank
     * @return the copy
     */
    public Recommendation withReason(String newReason) {
        return new Recommendation(recipe, coveragePercent, missing, favorite, rating, newReason);
    }
}
