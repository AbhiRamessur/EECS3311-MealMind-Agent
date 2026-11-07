package com.mealmind.agent;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import java.util.List;

/**
 * Scripted plan-edit replies and a small recipe book for the F06 tests. Every recipe has 1 serving and
 * one 100 g ingredient: E1 Lentil Soup (300 kcal, $1.00, 30 min), E2 Chicken Plate (meat; 500 kcal,
 * $4.00, 20 min), E3 Oat Bowl (400 kcal, $2.00, 5 min).
 */
final class PlanEditReplies {

    private PlanEditReplies() {
    }

    static Recipe recipe(String id, String name, String ingredient, double kcal, double cost, int prep) {
        return new Recipe(id, name, 1, prep, List.of("Cook"),
                List.of(new Ingredient(ingredient, 100, Unit.GRAM, kcal, 20, 40, 5, cost)));
    }

    static final Recipe LENTIL_SOUP = recipe("E1", "Lentil Soup", "Red lentils", 300, 1.00, 30);
    static final Recipe CHICKEN_PLATE = recipe("E2", "Chicken Plate", "Chicken breast", 500, 4.00, 20);
    static final Recipe OAT_BOWL = recipe("E3", "Oat Bowl", "Rolled oats", 400, 2.00, 5);

    static RecipeBook book() {
        RecipeBook book = new RecipeBook();
        book.put(LENTIL_SOUP);
        book.put(CHICKEN_PLATE);
        book.put(OAT_BOWL);
        return book;
    }

    /** {"edit": {"action": "replace", ...}} without servings. */
    static String replace(int day, String slot, String recipeId, String explanation) {
        return "{\"edit\": {\"action\": \"replace\", \"day\": " + day + ", \"slot\": \"" + slot + "\", \"recipeId\": \""
                + recipeId + "\", \"explanation\": \"" + explanation + "\"}}";
    }

    /** {"edit": {"action": "clear", ...}}. */
    static String clear(int day, String slot) {
        return "{\"edit\": {\"action\": \"clear\", \"day\": " + day + ", \"slot\": \"" + slot + "\", \"explanation\": \"Freed up\"}}";
    }

    /** {"edit": {"action": "clarify", ...}}. */
    static String clarify(String question) {
        return "{\"edit\": {\"action\": \"clarify\", \"question\": \"" + question + "\"}}";
    }
}
