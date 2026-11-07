package com.mealmind.gui.logic;

import java.util.List;

/**
 * The Recipes tab's detail pane for one recipe, as display texts (the same wording as {@code recipe show}).
 *
 * @param id               the recipe id
 * @param title            e.g. "R103 Red Lentil Soup"
 * @param summaryText      e.g. "Servings: 4 | Prep: 40 min"
 * @param ingredientLines  e.g. "200 g Red lentils" (unmodifiable)
 * @param instructionLines numbered steps, e.g. "1. Rinse the lentils." (unmodifiable)
 * @param perServingText   e.g. "Per serving: 331 kcal, ..."
 * @param ratingLine       e.g. "Your rating: 4/5 | Favorite: yes"
 * @param safetyLine       "Profile check: OK" or "Profile check: " plus the violations joined by "; "
 * @param aiEstimate       whether this is a saved AI recipe whose nutrition and cost are estimates
 */
public record RecipeDetail(String id, String title, String summaryText, List<String> ingredientLines,
                           List<String> instructionLines, String perServingText, String ratingLine, String safetyLine,
                           boolean aiEstimate) {

    /** The line shown for AI recipes. */
    public static final String AI_ESTIMATE_LINE = "Nutrition and cost values are AI estimates.";

    /** Copies the lists. */
    public RecipeDetail {
        ingredientLines = List.copyOf(ingredientLines);
        instructionLines = List.copyOf(instructionLines);
    }
}
