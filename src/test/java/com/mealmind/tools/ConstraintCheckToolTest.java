package com.mealmind.tools;

import static com.mealmind.tools.ToolTestSupport.error;
import static com.mealmind.tools.ToolTestSupport.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.RecipeBook;
import com.mealmind.persistence.RecipeRepository;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConstraintCheckToolTest {

    private UserProfile profile;
    private ConstraintCheckTool tool;

    @BeforeEach
    void setUp() {
        RecipeBook book = new RecipeBook();
        RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES).forEach(book::put);
        profile = new UserProfile();
        tool = new ConstraintCheckTool(book, new ConstraintValidator(profile));
    }

    @Test
    void reportsSafeAndUnsafeRecipesWithMessagesInOrder() {
        profile.setDiet(DietType.VEGETARIAN);
        profile.addAllergen("peanut");

        JsonNode results = ok(tool, "{\"recipeIds\": [\"R109\", \"R103\", \"R101\"]}").get("results");

        assertEquals(List.of("R109", "R103", "R101"), results.findValuesAsText("id"));
        assertFalse(results.get(0).get("safe").asBoolean());
        assertEquals("Peanut butter contains peanut, a listed allergen", results.get(0).get("violations").get(0).asText());
        assertTrue(results.get(1).get("safe").asBoolean());
        assertEquals(0, results.get(1).get("violations").size());
        assertTrue(results.get(2).get("violations").get(0).asText()
                .startsWith("Chicken breast is not allowed for a vegetarian diet"));
    }

    @Test
    void unknownIdIsNotFoundAndNotSafe() {
        JsonNode result = ok(tool, "{\"recipeIds\": [\"R999\"]}").get("results").get(0);

        assertFalse(result.get("found").asBoolean());
        assertFalse(result.get("safe").asBoolean());
        assertEquals(0, result.get("violations").size());
    }

    @Test
    void requiresOneToTenIds() {
        assertEquals("recipeIds must contain 1 to 10 ids, got 0", error(tool, "{\"recipeIds\": []}"));
        String eleven = "[" + "\"R101\",".repeat(10) + "\"R101\"]";
        assertEquals("recipeIds must contain 1 to 10 ids, got 11", error(tool, "{\"recipeIds\": " + eleven + "}"));
        assertEquals("Missing required argument 'recipeIds'", error(tool, "{}"));
    }

    @Test
    void profileIsReadLive() {
        assertTrue(ok(tool, "{\"recipeIds\": [\"R101\"]}").get("results").get(0).get("safe").asBoolean());

        profile.setDiet(DietType.VEGAN);

        assertFalse(ok(tool, "{\"recipeIds\": [\"R101\"]}").get("results").get(0).get("safe").asBoolean());
    }
}
