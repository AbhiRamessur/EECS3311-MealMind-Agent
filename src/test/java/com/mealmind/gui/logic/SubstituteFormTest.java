package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.substitution.SubstitutionReason;
import com.mealmind.substitution.SubstitutionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubstituteFormTest {

    private static List<FieldError> errors(String id, String ingredient, String reason) {
        FormResult<SubstitutionRequest> result = SubstituteForm.parse(id, ingredient, reason);
        assertTrue(!result.isValid());
        return result.errors();
    }

    @Test
    void validRequestKeepsTheFields() {
        SubstitutionRequest request = SubstituteForm.parse("R101", "Chicken breast", "Preference").value().orElseThrow();

        assertEquals("R101", request.recipeId());
        assertEquals("Chicken breast", request.ingredientName());
        assertEquals(SubstitutionReason.PREFERENCE, request.reason());
        assertEquals(SubstitutionReason.ALLERGY, SubstituteForm.parse("R101", "x", "  aLLergy ").value().orElseThrow().reason());
    }

    @Test
    void eachFieldHasItsOwnError() {
        assertEquals(List.of(new FieldError("recipe", "Select a recipe first")), errors(null, "x", "missing"));
        assertEquals(List.of(new FieldError("recipe", "Select a recipe first")), errors(" ", "x", "missing"));
        assertEquals(List.of(new FieldError("ingredient", "Choose an ingredient")), errors("R101", "", "missing"));
        assertEquals(List.of(new FieldError("reason", "Unknown reason 'banana'. Use: allergy, missing, preference")),
                errors("R101", "x", "banana"));
    }

    @Test
    void everyErrorIsCollected() {
        assertEquals(List.of("recipe", "ingredient", "reason"),
                errors("", null, "").stream().map(FieldError::field).toList());
    }

    @Test
    void ingredientNamesInRecipeOrder() {
        Recipe recipe = new Recipe("T1", "Bowl", 1, 5, List.of("Mix."), List.of(
                new Ingredient("Rice", 100, Unit.GRAM, 100, 2, 20, 1, 0.5), new Ingredient("Beans", 100, Unit.GRAM, 100, 7, 15, 1, 0.5)));

        assertEquals(List.of("Rice", "Beans"), SubstituteForm.ingredientNames(recipe));
    }
}
