package com.mealmind.substitution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientLocatorTest {

    private static final Recipe RECIPE = new Recipe("T1", "Stir-Fry", 2, 20, List.of("Cook."), Arrays.stream(
            new String[] {"Chicken breast", "Red bell pepper", "Green bell pepper", "Soy sauce", "Rice"})
            .map(name -> new Ingredient(name, 100, Unit.GRAM, 100, 10, 10, 4, 1.00)).toList());

    @Test
    void exactNameIgnoringCaseAndSpaces() {
        assertEquals(0, IngredientLocator.find(RECIPE, "Chicken breast"));
        assertEquals(3, IngredientLocator.find(RECIPE, "  SOY   sauce "));
        assertEquals(4, IngredientLocator.find(RECIPE, "rice"));
    }

    @Test
    void uniqueWholeWordMatch() {
        assertEquals(0, IngredientLocator.find(RECIPE, "chicken"));
        assertEquals(1, IngredientLocator.find(RECIPE, "red bell pepper"));
        assertEquals(2, IngredientLocator.find(RECIPE, "green"));
    }

    @Test
    void noMatchListsTheIngredients() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> IngredientLocator.find(RECIPE, "tofu"));
        assertEquals("Recipe T1 has no ingredient 'tofu'. Ingredients: Chicken breast, Red bell pepper, Green bell pepper, "
                + "Soy sauce, Rice", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> IngredientLocator.find(RECIPE, "chick"));
    }

    @Test
    void ambiguousMatchAsksForMore() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> IngredientLocator.find(RECIPE, "pepper"));
        assertEquals("'pepper' matches several ingredients: Red bell pepper, Green bell pepper. Be more specific.", e.getMessage());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IngredientLocator.find(RECIPE, " "));
        assertThrows(IllegalArgumentException.class, () -> IngredientLocator.find(RECIPE, null));
    }
}
