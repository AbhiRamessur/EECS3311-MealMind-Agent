package com.mealmind.planning;

import com.mealmind.model.Recipe;

/**
 * Prefers cheap recipes (a ConcreteStrategy). Score = minus the recipe's cost per serving.
 */
public final class BudgetConsciousStrategy implements PlanningStrategy {

    @Override
    public String id() {
        return "budget";
    }

    @Override
    public String displayName() {
        return "Budget-Conscious";
    }

    @Override
    public String promptGuidance() {
        return "Prefer recipes with a low cost per serving. "
                + "Reuse inexpensive staples across meals and avoid costly ingredients unless they are already in the pantry.";
    }

    @Override
    public double score(Recipe recipe, ScoringContext ctx) {
        return -recipe.costPerServing();
    }
}
