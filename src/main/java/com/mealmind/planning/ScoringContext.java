package com.mealmind.planning;

import com.mealmind.pantry.Pantry;
import java.util.Objects;

/**
 * What a {@link PlanningStrategy} may look at besides the recipe itself.
 *
 * @param pantry the pantry as it is now
 */
public record ScoringContext(Pantry pantry) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if {@code pantry} is null
     */
    public ScoringContext {
        Objects.requireNonNull(pantry, "pantry");
    }
}
