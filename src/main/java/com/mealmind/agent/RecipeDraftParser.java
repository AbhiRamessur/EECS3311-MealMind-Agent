package com.mealmind.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns the model's recipe JSON into a {@link Recipe}, deterministically. Model output is untrusted,
 * so every field is checked and <em>all</em> problems are reported together (each naming its field
 * path, with 0-based indexes such as {@code ingredients[2].unit}), so one repair prompt can fix them.
 *
 * <p>When the structure is valid, plausibility is also checked: each ingredient's calories must
 * roughly match its macros (4/4/9 kcal per gram of protein/carbs/fat), and calories per serving must
 * be between 50 and 2500. Unknown extra fields are ignored; numbers written as strings are rejected.
 */
public final class RecipeDraftParser {

    /**
     * The outcome of parsing.
     *
     * @param recipe   the recipe when there are no problems, otherwise empty
     * @param problems one message per problem (at most 8, then "... and N more")
     */
    public record Result(Optional<Recipe> recipe, List<String> problems) {

        /** Copies the problem list. */
        public Result {
            problems = List.copyOf(problems);
        }
    }

    private static final int MAX_PROBLEMS = 8;

    private RecipeDraftParser() {
    }

    /**
     * Parses a recipe draft.
     *
     * @param recipeNode the value of the {@code "recipe"} key
     * @param id         the id to give the recipe
     * @return the recipe, or the list of problems
     */
    public static Result parse(JsonNode recipeNode, String id) {
        List<String> problems = new ArrayList<>();
        if (recipeNode == null || !recipeNode.isObject()) {
            return new Result(Optional.empty(), List.of("recipe: must be a JSON object"));
        }
        String name = text(recipeNode.get("name"), 80, "name", problems);
        Integer servings = integer(recipeNode.get("servings"), 1, 12, "servings", problems);
        Integer prepMinutes = integer(recipeNode.get("prepMinutes"), 0, 600, "prepMinutes", problems);
        List<Ingredient> ingredients = ingredients(recipeNode.get("ingredients"), problems);
        List<String> steps = instructions(recipeNode.get("instructions"), problems);

        Recipe recipe = null;
        if (problems.isEmpty()) {
            checkPlausibility(ingredients, servings, problems);
        }
        if (problems.isEmpty()) {
            try {
                recipe = new Recipe(id, name, servings, prepMinutes, steps, ingredients);
            } catch (IllegalArgumentException e) {
                problems.add("recipe: " + e.getMessage());
            }
        }
        return new Result(Optional.ofNullable(recipe), limit(problems));
    }

    // ---- structure ----

    private static String text(JsonNode node, int maxLength, String path, List<String> problems) {
        if (node == null || !node.isTextual() || node.asText().isBlank() || node.asText().trim().length() > maxLength) {
            problems.add(path + ": must be a string of 1 to " + maxLength + " characters");
            return null;
        }
        return node.asText().trim();
    }

    private static Integer integer(JsonNode node, int min, int max, String path, List<String> problems) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.asInt() < min || node.asInt() > max) {
            problems.add(path + ": must be a whole number from " + min + " to " + max);
            return null;
        }
        return node.asInt();
    }

    private static Double number(JsonNode node, boolean positive, String path, List<String> problems) {
        boolean valid = node != null && node.isNumber() && Double.isFinite(node.asDouble())
                && (positive ? node.asDouble() > 0 : node.asDouble() >= 0);
        if (!valid) {
            problems.add(path + ": must be a number " + (positive ? "greater than 0" : "of 0 or more"));
            return null;
        }
        return node.asDouble();
    }

    private static List<Ingredient> ingredients(JsonNode node, List<String> problems) {
        if (node == null || !node.isArray() || node.size() < 2 || node.size() > 25) {
            problems.add("ingredients: must be an array of 2 to 25 ingredients");
            return List.of();
        }
        List<Ingredient> result = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            ingredient(node.get(i), "ingredients[" + i + "]", problems).ifPresent(result::add);
        }
        return result;
    }

    private static Optional<Ingredient> ingredient(JsonNode node, String path, List<String> problems) {
        if (!node.isObject()) {
            problems.add(path + ": must be a JSON object");
            return Optional.empty();
        }
        int before = problems.size();
        String name = text(node.get("name"), 80, path + ".name", problems);
        Double quantity = number(node.get("quantity"), true, path + ".quantity", problems);
        Unit unit = unit(node.get("unit"), path + ".unit", problems);
        Double calories = number(node.get("calories"), false, path + ".calories", problems);
        Double protein = number(node.get("proteinG"), false, path + ".proteinG", problems);
        Double carbs = number(node.get("carbsG"), false, path + ".carbsG", problems);
        Double fat = number(node.get("fatG"), false, path + ".fatG", problems);
        Double cost = number(node.get("cost"), false, path + ".cost", problems);
        if (problems.size() > before) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Ingredient(name, quantity, unit, calories, protein, carbs, fat, cost));
        } catch (IllegalArgumentException e) {
            problems.add(path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static Unit unit(JsonNode node, String path, List<String> problems) {
        if (node == null || !node.isTextual()) {
            problems.add(path + ": must be a string; use one of " + PromptBuilder.unitSymbols());
            return null;
        }
        try {
            return Unit.parse(node.asText());
        } catch (IllegalArgumentException e) {
            problems.add(path + ": unknown unit '" + node.asText().trim() + "'; use one of " + PromptBuilder.unitSymbols());
            return null;
        }
    }

    private static List<String> instructions(JsonNode node, List<String> problems) {
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > 20) {
            problems.add("instructions: must be an array of 1 to 20 steps");
            return List.of();
        }
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode step = node.get(i);
            if (!step.isTextual() || step.asText().isBlank() || step.asText().trim().length() > 500) {
                problems.add("instructions[" + i + "]: must be a non-blank string of at most 500 characters");
            } else {
                steps.add(step.asText().trim());
            }
        }
        return steps;
    }

    // ---- plausibility (only when the structure is valid) ----

    private static void checkPlausibility(List<Ingredient> ingredients, int servings, List<String> problems) {
        double totalCalories = 0;
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            double calories = ingredient.getCalories();
            double energy = 4 * ingredient.getProteinG() + 4 * ingredient.getCarbsG() + 9 * ingredient.getFatG();
            boolean hasEnergy = calories > 0 || energy > 0;
            if (hasEnergy && Math.abs(calories - energy) > Math.max(30, 0.35 * Math.max(calories, energy))) {
                problems.add(String.format(Locale.ROOT, "ingredients[%d].calories: %.0f kcal does not match its macros (about %.0f kcal)",
                        i, calories, energy));
            }
            totalCalories += calories;
        }
        double perServing = totalCalories / servings;
        if (perServing < 50 || perServing > 2500) {
            problems.add(String.format(Locale.ROOT, "Per-serving calories are %.0f; expected between 50 and 2500", perServing));
        }
    }

    private static List<String> limit(List<String> problems) {
        if (problems.size() <= MAX_PROBLEMS) {
            return problems;
        }
        List<String> limited = new ArrayList<>(problems.subList(0, MAX_PROBLEMS));
        limited.add("... and " + (problems.size() - MAX_PROBLEMS) + " more");
        return limited;
    }
}
