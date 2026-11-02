package com.mealmind.planning;

import com.mealmind.model.Recipe;

/**
 * A way of choosing recipes for a meal plan (Stage 1 F05: Pantry-First, Budget, High-Protein).
 *
 * <p><strong>Strategy pattern roles:</strong>
 * <ul>
 *   <li><em>Strategy</em>: {@code PlanningStrategy} (this interface);</li>
 *   <li><em>ConcreteStrategies</em>: {@link PantryFirstStrategy}, {@link BudgetConsciousStrategy} and
 *       {@link HighProteinStrategy};</li>
 *   <li><em>Context</em>: {@link RulePlanner} now, which calls {@link #score}; the AI planner of a later
 *       commit will use {@link #promptGuidance} for its system prompt.</li>
 * </ul>
 * This covers the Stage 1 responsibilities "system prompt construction" ({@link #promptGuidance}) and
 * "constraint weighting" ({@link #score}). Safety is never a strategy's job: the planner only scores
 * recipes that already passed the deterministic safety check.
 */
public interface PlanningStrategy {

    /**
     * Returns the id used on the command line.
     *
     * @return a lowercase kebab-case id, e.g. {@code "pantry-first"}
     */
    String id();

    /**
     * Returns a human-readable name.
     *
     * @return the display name, e.g. {@code "Pantry-First"}
     */
    String displayName();

    /**
     * Returns a short plain-text instruction paragraph for a later LLM-based planner.
     *
     * @return non-blank guidance
     */
    String promptGuidance();

    /**
     * Scores a recipe; higher is better.
     *
     * @param recipe the recipe to score (at its stated servings)
     * @param ctx    what the strategy may look at
     * @return the score
     */
    double score(Recipe recipe, ScoringContext ctx);
}
