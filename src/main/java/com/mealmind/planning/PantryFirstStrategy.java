package com.mealmind.planning;

import com.mealmind.model.Recipe;

/**
 * Prefers recipes the pantry already covers (a ConcreteStrategy). Score = coverage percent &times; 100
 * minus the number of missing ingredients, so coverage dominates and fewer missing items break ties.
 */
public final class PantryFirstStrategy implements PlanningStrategy {

    @Override
    public String id() {
        return "pantry-first";
    }

    @Override
    public String displayName() {
        return "Pantry-First";
    }

    @Override
    public String promptGuidance() {
        return "Prefer recipes that use ingredients already in the pantry. "
                + "Choose recipes with the fewest missing ingredients so the shopping list stays short.";
    }

    @Override
    public double score(Recipe recipe, ScoringContext ctx) {
        PantryCoverage.Result coverage = PantryCoverage.of(recipe, ctx.pantry());
        return coverage.percent() * 100.0 - coverage.missing().size();
    }
}
