package com.mealmind.agent;

import com.mealmind.llm.TokenUsage;
import com.mealmind.model.Recipe;
import com.mealmind.validation.ValidationResult;
import java.util.Objects;

/**
 * An AI-generated recipe candidate with its safety check result and the counters of the agent run
 * that produced it. It is held as a pending candidate and is not saved.
 *
 * <p>Nutrition and cost values are <strong>AI estimates</strong>, checked only for plausibility by
 * {@link RecipeDraftParser}; there is no nutrition database in this version.
 *
 * @param recipe        the candidate recipe
 * @param validation    the deterministic safety check of the recipe against the profile
 * @param llmCalls      model calls made
 * @param toolSteps     tool calls requested
 * @param repairs       repair prompts sent
 * @param safetyRetries drafts that failed the safety check
 * @param usage         tokens used
 */
public record GeneratedRecipe(Recipe recipe, ValidationResult validation, int llmCalls, int toolSteps, int repairs,
                              int safetyRetries, TokenUsage usage) {

    /**
     * Validates the references.
     *
     * @throws NullPointerException if a reference is null
     */
    public GeneratedRecipe {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(usage, "usage");
    }

    /**
     * Returns whether the recipe passed the safety check.
     *
     * @return {@code true} if there are no allergen or diet violations
     */
    public boolean safe() {
        return validation.isSafe();
    }
}
