package com.mealmind.agent;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds scripted model replies. Each ingredient is 100 g with 20 g protein, 50 g carbs, 10 g fat and
 * 370 kcal (4*20 + 4*50 + 9*10 = 370), so drafts pass the plausibility check.
 */
final class RecipeReplies {

    private RecipeReplies() {
    }

    /** One ingredient object with consistent nutrition. */
    static String ingredient(String name) {
        return "{\"name\": \"" + name + "\", \"quantity\": 100, \"unit\": \"g\", \"calories\": 370, "
                + "\"proteinG\": 20, \"carbsG\": 50, \"fatG\": 10, \"cost\": 1.5}";
    }

    /** The recipe object (the value of the "recipe" key): 2 servings, 20 minutes, two steps. */
    static String recipeObject(String name, String... ingredientNames) {
        String ingredients = Stream.of(ingredientNames).map(RecipeReplies::ingredient).collect(Collectors.joining(", "));
        return "{\"name\": \"" + name + "\", \"servings\": 2, \"prepMinutes\": 20, \"ingredients\": [" + ingredients
                + "], \"instructions\": [\"Cook everything.\", \"Serve warm.\"]}";
    }

    /** A complete final-answer reply. */
    static String recipe(String name, String... ingredientNames) {
        return "{\"recipe\": " + recipeObject(name, ingredientNames) + "}";
    }

    /** A tool-call reply. */
    static String toolCall(String tool, String argumentsJson) {
        return "{\"tool\": \"" + tool + "\", \"arguments\": " + argumentsJson + "}";
    }
}
