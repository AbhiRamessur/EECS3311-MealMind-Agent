package com.mealmind.planning;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The available {@link PlanningStrategy} instances, looked up by id.
 */
public final class PlanningStrategies {

    private static final List<PlanningStrategy> ALL = List.of(
            new PantryFirstStrategy(), new BudgetConsciousStrategy(), new HighProteinStrategy());

    private PlanningStrategies() {
    }

    /**
     * Returns every strategy.
     *
     * @return an unmodifiable list in the order pantry-first, budget, high-protein
     */
    public static List<PlanningStrategy> all() {
        return ALL;
    }

    /**
     * Finds a strategy by id, ignoring case and surrounding whitespace.
     *
     * @param id the id, e.g. {@code "Pantry-First"}
     * @return the strategy
     * @throws IllegalArgumentException if no strategy has that id (also for null)
     */
    public static PlanningStrategy byId(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(s -> s.id().equals(wanted)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy '" + (id == null ? "" : id.trim())
                        + "'. Available: " + ALL.stream().map(PlanningStrategy::id).collect(Collectors.joining(", "))));
    }
}
