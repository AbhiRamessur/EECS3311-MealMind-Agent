package com.mealmind.planning;

import com.mealmind.model.DayPlan;
import com.mealmind.model.MealType;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * What to generate.
 *
 * @param days     the number of days, 1 to 7
 * @param strategy how to choose recipes
 * @param slots    1 to 4 distinct meal types to fill each day, in fill order (copied)
 * @param budget   the budget for the whole plan in dollars, or null for none
 */
public record PlanRequest(int days, PlanningStrategy strategy, List<MealType> slots, Double budget) {

    /** Every {@link MealType}: at most this many slots per day. */
    public static final int MAX_SLOTS = MealType.values().length;

    /**
     * Validates and copies the fields.
     *
     * @throws IllegalArgumentException if {@code days} is out of range, the slots are empty, too many,
     *                                  repeated or contain null, or the budget is not a finite number above 0
     * @throws NullPointerException     if {@code strategy} or {@code slots} is null
     */
    public PlanRequest {
        if (days < 1 || days > DayPlan.MAX_DAYS) {
            throw new IllegalArgumentException("Days must be between 1 and " + DayPlan.MAX_DAYS + ", got " + days);
        }
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(slots, "slots");
        if (slots.isEmpty() || slots.size() > MAX_SLOTS || slots.stream().anyMatch(Objects::isNull)
                || new HashSet<>(slots).size() != slots.size()) {
            throw new IllegalArgumentException("Slots must be 1 to " + MAX_SLOTS + " different meal types");
        }
        slots = List.copyOf(slots);
        if (budget != null && (!Double.isFinite(budget) || budget <= 0)) {
            throw new IllegalArgumentException("Budget must be a number above 0, got " + budget);
        }
    }

    /**
     * Returns the defaults: 7 days, pantry-first, lunch and dinner, no budget.
     *
     * @return the default request
     */
    public static PlanRequest defaults() {
        return new PlanRequest(DayPlan.MAX_DAYS, PlanningStrategies.byId("pantry-first"),
                List.of(MealType.LUNCH, MealType.DINNER), null);
    }
}
