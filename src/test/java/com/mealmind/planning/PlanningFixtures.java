package com.mealmind.planning;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import java.util.List;

/**
 * Round-number recipes for the planning tests (all 1 serving):
 * <pre>
 *   id    name            cost  protein  ingredients
 *   P1    Rice Bowl       1.00   5 g     Rice 100 g
 *   P2    Chicken Plate   6.00  40 g     Chicken 200 g
 *   P3    Tofu Stir-Fry   4.00  25 g     Tofu 200 g, Rice 100 g
 *   P4    Peanut Noodles  3.00  15 g     Noodles 100 g, Peanut butter 30 g
 * </pre>
 */
final class PlanningFixtures {

    static final Recipe RICE_BOWL = recipe("P1", "Rice Bowl", grams("Rice", 100, 5, 1.00));
    static final Recipe CHICKEN_PLATE = recipe("P2", "Chicken Plate", grams("Chicken", 200, 40, 6.00));
    static final Recipe TOFU_STIR_FRY = recipe("P3", "Tofu Stir-Fry",
            grams("Tofu", 200, 20, 3.00), grams("Rice", 100, 5, 1.00));
    static final Recipe PEANUT_NOODLES = recipe("P4", "Peanut Noodles",
            grams("Noodles", 100, 10, 2.00), grams("Peanut butter", 30, 5, 1.00));

    private PlanningFixtures() {
    }

    static RecipeBook book(Recipe... recipes) {
        RecipeBook book = new RecipeBook();
        for (Recipe recipe : recipes) {
            book.put(recipe);
        }
        return book;
    }

    static RecipeBook allFour() {
        return book(RICE_BOWL, CHICKEN_PLATE, TOFU_STIR_FRY, PEANUT_NOODLES);
    }

    static Recipe recipe(String id, String name, Ingredient... ingredients) {
        return new Recipe(id, name, 1, 10, List.of("Cook"), List.of(ingredients));
    }

    /** An ingredient with 100 kcal and no carbs or fat, so only protein and cost vary. */
    static Ingredient grams(String name, double quantity, double proteinG, double cost) {
        return new Ingredient(name, quantity, Unit.GRAM, 100, proteinG, 0, 0, cost);
    }
}
