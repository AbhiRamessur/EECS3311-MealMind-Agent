package com.mealmind.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationFormatterTest {

    private static Recommendation item(String id, String name, String reason) {
        Recipe recipe = new Recipe(id, name, 1, 10, List.of("Cook"), List.of(new Ingredient("Oats", 100, Unit.GRAM, 100, 5, 10, 2, 1)));
        return new Recommendation(recipe, 0, List.of("Oats"), false, null, reason);
    }

    @Test
    void notesFirstThenNumberedLines() {
        RecommendationResult result = new RecommendationResult(
                List.of(item("R103", "Red Lentil Soup", "Covers 50% of your pantry; 40 min"), item("R105", "Black Bean Tacos", "Quick")),
                List.of("Pantry is empty; ranking uses your ratings and favorites only."));

        assertEquals("""
                Note: Pantry is empty; ranking uses your ratings and favorites only.
                1. R103 Red Lentil Soup - Covers 50% of your pantry; 40 min
                2. R105 Black Bean Tacos - Quick
                """, RecommendationFormatter.toText(result));
    }

    @Test
    void emptyResult() {
        assertEquals("No recommendations.\n", RecommendationFormatter.toText(new RecommendationResult(List.of(), List.of())));
        assertEquals("Note: n\nNo recommendations.\n", RecommendationFormatter.toText(new RecommendationResult(List.of(), List.of("n"))));
    }
}
