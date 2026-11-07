package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeScaler;
import com.mealmind.model.Unit;
import com.mealmind.recommend.RecommendationRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ScaleForm and RecommendForm. */
class RecipeFormsTest {

    private static final Recipe ONE = new Recipe("T1", "Toast", 1, 5, List.of("Toast it"),
            List.of(new Ingredient("Bread", 50, Unit.GRAM, 130, 4, 24, 2, 0.3)));

    @Test
    void scaleFormBoundsAndMessages() {
        assertEquals(1, ScaleForm.parseServings(" 1 ").value().orElseThrow());
        assertEquals(12, ScaleForm.parseServings("12").value().orElseThrow());
        String domain = assertThrows(IllegalArgumentException.class, () -> RecipeScaler.scale(ONE, 0)).getMessage();
        assertEquals(List.of(new FieldError("servings", domain)), ScaleForm.parseServings("0").errors());
        assertEquals(List.of(new FieldError("servings", "Target servings must be at most 12, got 13")),
                ScaleForm.parseServings("13").errors());
        for (String text : new String[] {"", "six", "2.5", null}) {
            assertEquals(List.of(new FieldError("servings", "must be a number")), ScaleForm.parseServings(text).errors());
        }
    }

    @Test
    void recommendFormValidInputAndDefaults() {
        RecommendationRequest plain = RecommendForm.parse(false, "  ", RecommendForm.DEFAULT_LIMIT).value().orElseThrow();
        assertEquals(RecommendationRequest.defaults(), plain);
        assertEquals("5", RecommendForm.DEFAULT_LIMIT);

        RecommendationRequest custom = RecommendForm.parse(true, " 30 ", "10").value().orElseThrow();
        assertEquals(new RecommendationRequest(true, 30, 10), custom);
        assertNull(RecommendForm.parse(true, null, "1").value().orElseThrow().maxPrepMinutes());
    }

    @Test
    void recommendFormUsesTheDomainMessagesAndCollectsEveryError() {
        String minutes = assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(false, 0, 5)).getMessage();
        String low = assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(false, null, 0)).getMessage();
        String high = assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(false, null, 11)).getMessage();

        assertEquals(List.of(new FieldError("maxMinutes", minutes)), RecommendForm.parse(false, "0", "5").errors());
        assertEquals(List.of(new FieldError("limit", low)), RecommendForm.parse(false, "", "0").errors());
        assertEquals(List.of(new FieldError("limit", high)), RecommendForm.parse(false, "", "11").errors());
        assertEquals(List.of(new FieldError("maxMinutes", "must be a number"), new FieldError("limit", "must be a number")),
                RecommendForm.parse(false, "soon", "").errors());
        assertEquals(List.of(new FieldError("maxMinutes", minutes), new FieldError("limit", high)),
                RecommendForm.parse(false, "0", "11").errors());
        assertTrue(RecommendForm.parse(false, "1", "1").isValid());
    }
}
