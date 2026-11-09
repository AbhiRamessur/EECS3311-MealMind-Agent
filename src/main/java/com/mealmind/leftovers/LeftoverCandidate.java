package com.mealmind.leftovers;

import com.mealmind.model.Recipe;
import java.util.List;
import java.util.Objects;

/**
 * A safe recipe from the recipe book that uses at least one expiring item.
 *
 * @param recipe          the recipe
 * @param uses            the expiring items it uses (unmodifiable, non-empty)
 * @param coveragePercent how much of it the pantry covers, 0 to 100
 * @param missing         uncovered ingredient names (unmodifiable)
 * @param favorite        whether the user marked it as a favourite
 * @param rating          the user's rating, or null
 */
public record LeftoverCandidate(Recipe recipe, List<ExpiringItem> uses, int coveragePercent, List<String> missing,
                                boolean favorite, Integer rating) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException     if {@code recipe} or a list is null
     * @throws IllegalArgumentException if {@code uses} is empty
     */
    public LeftoverCandidate {
        Objects.requireNonNull(recipe, "recipe");
        uses = List.copyOf(uses);
        missing = List.copyOf(missing);
        if (uses.isEmpty()) {
            throw new IllegalArgumentException("A leftover candidate must use at least one expiring item");
        }
    }
}
