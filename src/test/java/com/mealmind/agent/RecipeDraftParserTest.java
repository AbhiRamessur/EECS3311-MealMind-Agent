package com.mealmind.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RecipeDraftParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** A valid two-ingredient draft (370 kcal each, 2 servings: 370 kcal per serving). */
    private static ObjectNode draft() {
        try {
            return (ObjectNode) JSON.readTree(RecipeReplies.recipeObject("  Rice and Beans  ", "Rice", "Beans"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectNode firstIngredient(ObjectNode draft) {
        return (ObjectNode) draft.get("ingredients").get(0);
    }

    /** Applies one change to a valid draft and returns the problems. */
    private static List<String> problemsAfter(Consumer<ObjectNode> change) {
        ObjectNode node = draft();
        change.accept(node);
        RecipeDraftParser.Result result = RecipeDraftParser.parse(node, "AI001");
        assertTrue(result.recipe().isEmpty());
        return result.problems();
    }

    private static void assertSingleProblem(String expected, Consumer<ObjectNode> change) {
        assertEquals(List.of(expected), problemsAfter(change));
    }

    @Test
    void validDraftBecomesRecipe() {
        RecipeDraftParser.Result result = RecipeDraftParser.parse(draft(), "AI001");

        assertEquals(List.of(), result.problems());
        Recipe recipe = result.recipe().orElseThrow();
        assertEquals("AI001", recipe.getId());
        assertEquals("Rice and Beans", recipe.getName());
        assertEquals(2, recipe.getServings());
        assertEquals(20, recipe.getPrepMinutes());
        assertEquals(Unit.GRAM, recipe.getIngredients().get(0).getUnit());
        assertEquals(740, recipe.getCalories(), 1e-9);
        assertEquals(List.of("Cook everything.", "Serve warm."), recipe.getInstructions());
    }

    @Test
    void notAnObject() {
        assertEquals(List.of("recipe: must be a JSON object"), RecipeDraftParser.parse(JSON.createArrayNode(), "AI001").problems());
    }

    @Test
    void topLevelFieldRules() {
        assertSingleProblem("name: must be a string of 1 to 80 characters", d -> d.put("name", " "));
        assertSingleProblem("name: must be a string of 1 to 80 characters", d -> d.put("name", "x".repeat(81)));
        assertSingleProblem("servings: must be a whole number from 1 to 12", d -> d.put("servings", 0));
        assertSingleProblem("servings: must be a whole number from 1 to 12", d -> d.put("servings", 13));
        assertSingleProblem("servings: must be a whole number from 1 to 12", d -> d.put("servings", "2"));
        assertSingleProblem("prepMinutes: must be a whole number from 0 to 600", d -> d.put("prepMinutes", -1));
        assertSingleProblem("prepMinutes: must be a whole number from 0 to 600", d -> d.put("prepMinutes", 601));
    }

    @Test
    void ingredientCountRules() {
        assertSingleProblem("ingredients: must be an array of 2 to 25 ingredients",
                d -> ((ArrayNode) d.get("ingredients")).remove(1));
        assertSingleProblem("ingredients: must be an array of 2 to 25 ingredients", d -> {
            ArrayNode list = (ArrayNode) d.get("ingredients");
            for (int i = 0; i < 24; i++) {
                list.add(list.get(0).deepCopy());
            }
        });
    }

    @Test
    void ingredientFieldRulesNameTheFieldPath() {
        assertSingleProblem("ingredients[0].quantity: must be a number greater than 0", d -> firstIngredient(d).put("quantity", 0));
        assertSingleProblem("ingredients[0].unit: unknown unit 'pinch'; use one of " + PromptBuilder.unitSymbols(),
                d -> firstIngredient(d).put("unit", "pinch"));
        assertSingleProblem("ingredients[0].fatG: must be a number of 0 or more", d -> firstIngredient(d).put("fatG", -1));
        assertSingleProblem("ingredients[0].cost: must be a number of 0 or more", d -> firstIngredient(d).put("cost", "1.50"));
        assertSingleProblem("ingredients[0].name: must be a string of 1 to 80 characters", d -> firstIngredient(d).remove("name"));
    }

    @Test
    void unitProblemListsSymbolsFromTheUnitEnum() {
        String problem = problemsAfter(d -> firstIngredient(d).put("unit", "handful")).get(0);

        for (Unit unit : Unit.values()) {
            assertTrue(problem.contains(unit.getSymbol()), unit.name());
        }
    }

    @Test
    void instructionRules() {
        assertSingleProblem("instructions: must be an array of 1 to 20 steps", d -> d.put("instructions", "Cook it"));
        assertSingleProblem("instructions[1]: must be a non-blank string of at most 500 characters",
                d -> ((ArrayNode) d.get("instructions")).set(1, JSON.getNodeFactory().textNode("  ")));
        assertSingleProblem("instructions[0]: must be a non-blank string of at most 500 characters",
                d -> ((ArrayNode) d.get("instructions")).set(0, JSON.getNodeFactory().textNode("x".repeat(501))));
    }

    @Test
    void allProblemsAreCollectedAndCappedAtEight() {
        List<String> three = problemsAfter(d -> {
            d.put("servings", 0);
            d.put("prepMinutes", -5);
            firstIngredient(d).put("unit", "pinch");
        });
        assertEquals(3, three.size());

        List<String> many = problemsAfter(d -> {
            d.remove("name");
            d.put("servings", 0);
            d.put("prepMinutes", -5);
            d.put("instructions", "x");
            for (String field : new String[] {"quantity", "unit", "calories", "proteinG", "carbsG", "fatG", "cost"}) {
                firstIngredient(d).remove(field);
            }
        });
        assertEquals(9, many.size());
        assertEquals("... and 3 more", many.get(8));
    }

    @Test
    void extraFieldsAreIgnored() {
        ObjectNode node = draft();
        node.put("cuisine", "Mexican");
        firstIngredient(node).put("organic", true);

        assertTrue(RecipeDraftParser.parse(node, "AI001").recipe().isPresent());
    }

    @Test
    void plausibilityChecks() {
        assertSingleProblem("ingredients[0].calories: 1000 kcal does not match its macros (about 370 kcal)",
                d -> firstIngredient(d).put("calories", 1000));

        ObjectNode withSpice = draft();
        ((ArrayNode) withSpice.get("ingredients")).addObject().put("name", "Ground cumin").put("quantity", 1)
                .put("unit", "tsp").put("calories", 0).put("proteinG", 0).put("carbsG", 0).put("fatG", 0).put("cost", 0.1);
        assertTrue(RecipeDraftParser.parse(withSpice, "AI001").recipe().isPresent());

        assertSingleProblem("Per-serving calories are 49; expected between 50 and 2500", d -> {
            for (int i = 0; i < 2; i++) {
                ((ObjectNode) d.get("ingredients").get(i)).put("calories", 49).put("proteinG", 0).put("carbsG", 12.25).put("fatG", 0);
            }
        });
        assertSingleProblem("Per-serving calories are 2501; expected between 50 and 2500", d -> {
            d.put("servings", 1);
            ((ObjectNode) d.get("ingredients").get(0)).put("calories", 2131).put("proteinG", 100).put("carbsG", 300).put("fatG", 59);
        });
    }

    @Test
    void plausibilityIsNotReportedWhenStructureIsInvalid() {
        List<String> problems = problemsAfter(d -> {
            firstIngredient(d).put("calories", 5000);
            d.put("servings", 0);
        });

        assertEquals(List.of("servings: must be a whole number from 1 to 12"), problems);
        assertFalse(problems.stream().anyMatch(p -> p.contains("macros")));
    }
}
