package com.mealmind.planning;

import com.mealmind.model.Recipe;

/**
 * Prefers protein-rich recipes (a ConcreteStrategy). Score = protein grams per serving.
 */
public final class HighProteinStrategy implements PlanningStrategy {

    @Override
    public String id() {
        return "high-protein";
    }

    @Override
    public String displayName() {
        return "High-Protein";
    }

    @Override
    public String promptGuidance() {
        return "Prefer recipes with the most protein per serving. "
                + "Spread protein across the day's meals while staying near the user's calorie target.";
    }

    @Override
    public double score(Recipe recipe, ScoringContext ctx) {
        return recipe.proteinPerServing();
    }
}
