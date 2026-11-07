package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.recommend.Recommendation;
import com.mealmind.recommend.RecommendationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendRowsTest {

    private static Recommendation item(String id, String name, String reason) {
        Recipe recipe = new Recipe(id, name, 1, 10, List.of("Cook"), List.of(new Ingredient("Oats", 100, Unit.GRAM, 100, 5, 10, 2, 1)));
        return new Recommendation(recipe, 0, List.of("Oats"), false, null, reason);
    }

    @Test
    void ranksStartAtOneAndNotesArePreserved() {
        RecommendationResult result = new RecommendationResult(
                List.of(item("R103", "Red Lentil Soup", "Warm"), item("R105", "Black Bean Tacos", "Quick")), List.of("Note A", "Note B"));

        assertEquals(List.of(new RecommendRow("1", "R103", "Red Lentil Soup", "Warm"), new RecommendRow("2", "R105", "Black Bean Tacos", "Quick")),
                RecommendRows.of(result));
        assertEquals(List.of("Note A", "Note B"), RecommendRows.notes(result));
    }

    @Test
    void emptyResult() {
        RecommendationResult empty = new RecommendationResult(List.of(), List.of());

        assertEquals(List.of(), RecommendRows.of(empty));
        assertEquals(List.of(), RecommendRows.notes(empty));
    }
}
