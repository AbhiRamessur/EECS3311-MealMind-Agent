package com.mealmind.agent;

import com.mealmind.substitution.SubstitutionOption;
import java.util.List;

/**
 * The substitutes from one accepted (or unsafe) model answer, after the safety filter.
 *
 * @param options          the safe options, best first (unmodifiable; empty only for an unsafe answer)
 * @param removedForSafety how many suggested options the safety check dropped
 */
public record SubstitutionBatch(List<SubstitutionOption> options, int removedForSafety) {

    /**
     * Copies the list.
     *
     * @throws NullPointerException     if the list or an element is null
     * @throws IllegalArgumentException if {@code removedForSafety} is negative
     */
    public SubstitutionBatch {
        options = List.copyOf(options);
        if (removedForSafety < 0) {
            throw new IllegalArgumentException("removedForSafety must not be negative");
        }
    }
}
