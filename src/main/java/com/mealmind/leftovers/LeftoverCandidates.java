package com.mealmind.leftovers;

import com.mealmind.model.MealPlan.Slot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@link LeftoverPlanner#findCandidates}: the expiring items, the safe candidate recipes
 * in deterministic order, the eligible plan slots in fill order, notes, and why nothing can be done.
 *
 * @param expiring   the expiring items, most urgent first (unmodifiable)
 * @param candidates the candidates, best first (unmodifiable)
 * @param slots      the slots meals may go into: empty ones first, then filled ones when overwriting (unmodifiable)
 * @param notes      plain-text notes (unmodifiable)
 * @param blocked    a user-readable reason when there is nothing to do; empty otherwise
 */
public record LeftoverCandidates(List<ExpiringItem> expiring, List<LeftoverCandidate> candidates, List<Slot> slots,
                                 List<String> notes, Optional<String> blocked) {

    /**
     * Copies the lists.
     *
     * @throws NullPointerException if a field or element is null
     */
    public LeftoverCandidates {
        expiring = List.copyOf(expiring);
        candidates = List.copyOf(candidates);
        slots = List.copyOf(slots);
        notes = List.copyOf(notes);
        Objects.requireNonNull(blocked, "blocked");
    }

    /**
     * Returns whether there is nothing to propose.
     *
     * @return {@code true} when {@link #blocked()} holds a reason
     */
    public boolean isBlocked() {
        return blocked.isPresent();
    }
}
