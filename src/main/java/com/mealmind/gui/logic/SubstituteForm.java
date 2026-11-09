package com.mealmind.gui.logic;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.substitution.SubstitutionReason;
import com.mealmind.substitution.SubstitutionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses the Recipes tab's "Substitute an ingredient" form into a {@link SubstitutionRequest} (fields "recipe",
 * "ingredient" and "reason"; every problem is collected). Nothing is executed.
 */
public final class SubstituteForm {

    /** Shown when no recipe is selected. */
    public static final String SELECT_RECIPE = "Select a recipe first";
    /** Shown when no ingredient is chosen. */
    public static final String CHOOSE_INGREDIENT = "Choose an ingredient";

    private SubstituteForm() {
    }

    /**
     * Parses the form.
     *
     * @param recipeId       the selected recipe's id, or null/blank when none is selected
     * @param ingredientName the chosen ingredient, or null/blank
     * @param reasonText     the reason, e.g. "Preference" (any case, surrounding spaces ignored)
     * @return the request, or every field error
     */
    public static FormResult<SubstitutionRequest> parse(String recipeId, String ingredientName, String reasonText) {
        List<FieldError> errors = new ArrayList<>();
        if (recipeId == null || recipeId.isBlank()) {
            errors.add(new FieldError("recipe", SELECT_RECIPE));
        }
        if (ingredientName == null || ingredientName.isBlank()) {
            errors.add(new FieldError("ingredient", CHOOSE_INGREDIENT));
        }
        SubstitutionReason reason = null;
        try {
            reason = SubstitutionReason.parse(reasonText);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError("reason", e.getMessage()));
        }
        if (!errors.isEmpty()) {
            return FormResult.failed(errors);
        }
        return FormResult.ok(new SubstitutionRequest(recipeId, ingredientName, reason));
    }

    /**
     * Returns the recipe's ingredient names, in recipe order, for the ingredient choice.
     *
     * @param recipe the recipe
     * @return the names
     * @throws NullPointerException if {@code recipe} is null
     */
    public static List<String> ingredientNames(Recipe recipe) {
        return Objects.requireNonNull(recipe, "recipe").getIngredients().stream().map(Ingredient::getName).toList();
    }
}
