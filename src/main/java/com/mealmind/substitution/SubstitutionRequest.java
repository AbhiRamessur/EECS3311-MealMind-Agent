package com.mealmind.substitution;

import java.util.Objects;

/**
 * What the user asked to replace.
 *
 * @param recipeId       the recipe id, e.g. "R101"
 * @param ingredientName the ingredient as the user named it, e.g. "chicken"
 * @param reason         why it should be replaced
 */
public record SubstitutionRequest(String recipeId, String ingredientName, SubstitutionReason reason) {

    /**
     * Validates the fields and trims the strings.
     *
     * @throws IllegalArgumentException if a string is null or blank
     * @throws NullPointerException     if {@code reason} is null
     */
    public SubstitutionRequest {
        if (recipeId == null || recipeId.isBlank()) {
            throw new IllegalArgumentException("Recipe id must not be blank");
        }
        if (ingredientName == null || ingredientName.isBlank()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }
        Objects.requireNonNull(reason, "reason");
        recipeId = recipeId.trim();
        ingredientName = ingredientName.trim();
    }
}
