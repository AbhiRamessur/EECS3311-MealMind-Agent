package com.mealmind.leftovers;

import com.mealmind.model.MealPlan;
import java.util.List;
import java.util.Objects;

/**
 * Proposed leftover meals for one plan. Nothing changes until it is passed to {@link LeftoverPlanner#apply}.
 *
 * @param plan        the plan the proposal was made for (compared by identity when applying)
 * @param assignments the proposed meals, in slot order (unmodifiable)
 * @param expiring    the expiring items (unmodifiable)
 * @param notes       plain-text notes (unmodifiable)
 */
public record LeftoverProposal(MealPlan plan, List<LeftoverAssignment> assignments, List<ExpiringItem> expiring,
                               List<String> notes) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException if a field or element is null
     */
    public LeftoverProposal {
        Objects.requireNonNull(plan, "plan");
        assignments = List.copyOf(assignments);
        expiring = List.copyOf(expiring);
        notes = List.copyOf(notes);
    }
}
