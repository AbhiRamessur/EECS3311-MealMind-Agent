package com.mealmind.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Names;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.pantry.Pantry;
import com.mealmind.validation.ConstraintValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * {@code recipe_search}: finds recipes, or returns one recipe's full details by id (F03, deterministic part).
 *
 * <p><strong>Safety rule:</strong> recipes with any allergen or diet violation for the current profile
 * are never returned, so the language model never sees them; searches only report how many matching
 * recipes were hidden ({@code excludedForSafety}), and asking for an unsafe recipe by id gives an error
 * that does not say why.
 *
 * <p><strong>Pantry coverage:</strong> an ingredient is covered when the pantry holds at least the
 * required quantity in a compatible unit (incompatible units count as not covered). Expiry is ignored.
 * The {@code query} filter is a simple case-insensitive substring match on the recipe name and its
 * ingredient names. Read-only.
 */
public final class RecipeSearchTool implements Tool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final double EPSILON = 1e-9;

    private static final ToolSpec SPEC = new ToolSpec("recipe_search",
            "Searches recipes that are safe for the user's diet and allergies, with pantry coverage. "
                    + "Give 'id' alone for one recipe's full details, or any of the filters to search.",
            List.of(ToolParam.optional("id", ParamType.STRING, "Recipe id for full details, e.g. R101; not combinable with filters"),
                    ToolParam.optional("query", ParamType.STRING, "Text to find in the recipe name or ingredient names"),
                    ToolParam.optional("maxPrepMinutes", ParamType.INTEGER, "Maximum preparation time in minutes (0 or more)"),
                    ToolParam.optional("maxCaloriesPerServing", ParamType.INTEGER, "Maximum calories per serving (1 or more)"),
                    ToolParam.optional("pantryFirst", ParamType.BOOLEAN, "Sort by how much of each recipe the pantry already covers"),
                    ToolParam.optional("limit", ParamType.INTEGER, "Maximum results, 1 to " + MAX_LIMIT + " (default " + DEFAULT_LIMIT + ")")));

    private final RecipeBook recipeBook;
    private final Pantry pantry;
    private final ConstraintValidator validator;

    /** Pantry coverage of one recipe. */
    private record Coverage(int percent, List<String> missing) {
    }

    /**
     * Creates the tool.
     *
     * @param recipeBook the recipes to search
     * @param pantry     the pantry used for coverage
     * @param validator  the safety check against the current profile
     * @throws NullPointerException if an argument is null
     */
    public RecipeSearchTool(RecipeBook recipeBook, Pantry pantry, ConstraintValidator validator) {
        this.recipeBook = Objects.requireNonNull(recipeBook, "recipeBook");
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        if (arguments.has("id")) {
            if (arguments.size() > 1) {
                throw new IllegalArgumentException("'id' cannot be combined with search filters");
            }
            return details(arguments.get("id").asText());
        }
        return search(arguments);
    }

    private JsonNode details(String id) {
        Recipe recipe = recipeBook.get(id).orElseThrow(() -> new NoSuchElementException("No recipe with id '" + id + "'"));
        if (!validator.isSafe(recipe)) {
            throw new IllegalArgumentException("Recipe " + id + " is not available for this user's profile");
        }
        ObjectNode node = basics(recipe);
        ArrayNode steps = node.putArray("instructions");
        recipe.getInstructions().forEach(steps::add);
        ArrayNode ingredients = node.putArray("ingredients");
        for (Ingredient ingredient : recipe.getIngredients()) {
            ingredients.addObject()
                    .put("name", ingredient.getName())
                    .put("quantity", ToolJson.round1(ingredient.getQuantity()))
                    .put("unit", ingredient.getUnit().getSymbol());
        }
        return addNutritionAndCoverage(node, recipe);
    }

    private JsonNode search(JsonNode arguments) {
        int limit = arguments.path("limit").asInt(DEFAULT_LIMIT);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        if (arguments.has("maxPrepMinutes") && arguments.get("maxPrepMinutes").asInt() < 0) {
            throw new IllegalArgumentException("maxPrepMinutes must be 0 or more");
        }
        if (arguments.has("maxCaloriesPerServing") && arguments.get("maxCaloriesPerServing").asInt() < 1) {
            throw new IllegalArgumentException("maxCaloriesPerServing must be 1 or more");
        }
        String query = arguments.has("query") ? Names.normalize(arguments.get("query").asText()) : "";
        boolean pantryFirst = arguments.path("pantryFirst").asBoolean(false);

        List<Recipe> matches = new ArrayList<>();
        int excludedForSafety = 0;
        for (Recipe recipe : recipeBook.getAll()) {
            if (!matchesFilters(recipe, query, arguments)) {
                continue;
            }
            if (validator.isSafe(recipe)) {
                matches.add(recipe);
            } else {
                excludedForSafety++;
            }
        }
        Comparator<Recipe> order = Comparator.comparing(Recipe::getId);
        if (pantryFirst) {
            order = Comparator.comparingInt((Recipe r) -> -coverage(r).percent())
                    .thenComparingInt(r -> coverage(r).missing().size())
                    .thenComparing(Recipe::getId);
        }
        matches.sort(order);

        ObjectNode result = ToolJson.MAPPER.createObjectNode();
        result.put("totalMatches", matches.size());
        List<Recipe> returned = matches.subList(0, Math.min(limit, matches.size()));
        result.put("returned", returned.size());
        result.put("excludedForSafety", excludedForSafety);
        ArrayNode recipes = result.putArray("recipes");
        returned.forEach(recipe -> recipes.add(summary(recipe)));
        return result;
    }

    private static boolean matchesFilters(Recipe recipe, String query, JsonNode arguments) {
        if (!query.isEmpty() && !Names.normalize(recipe.getName()).contains(query)
                && recipe.getIngredients().stream().noneMatch(i -> i.normalizedName().contains(query))) {
            return false;
        }
        if (arguments.has("maxPrepMinutes") && recipe.getPrepMinutes() > arguments.get("maxPrepMinutes").asInt()) {
            return false;
        }
        return !arguments.has("maxCaloriesPerServing")
                || recipe.caloriesPerServing() <= arguments.get("maxCaloriesPerServing").asInt() + EPSILON;
    }

    /** {@code {"id", "name", "servings", "prepMinutes", "perServing", "pantryCoveragePercent", "missingIngredients"}}. */
    private ObjectNode summary(Recipe recipe) {
        return addNutritionAndCoverage(basics(recipe), recipe);
    }

    private static ObjectNode basics(Recipe recipe) {
        ObjectNode node = ToolJson.MAPPER.createObjectNode();
        node.put("id", recipe.getId());
        node.put("name", recipe.getName());
        node.put("servings", recipe.getServings());
        node.put("prepMinutes", recipe.getPrepMinutes());
        return node;
    }

    private ObjectNode addNutritionAndCoverage(ObjectNode node, Recipe recipe) {
        Coverage coverage = coverage(recipe);
        node.set("perServing", ToolJson.nutrition(recipe, recipe.getServings()));
        node.put("pantryCoveragePercent", coverage.percent());
        ArrayNode missing = node.putArray("missingIngredients");
        coverage.missing().forEach(missing::add);
        return node;
    }

    private Coverage coverage(Recipe recipe) {
        List<String> missing = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!isCovered(ingredient)) {
                missing.add(ingredient.getName());
            }
        }
        int total = recipe.getIngredients().size();
        int percent = (int) Math.round((total - missing.size()) * 100.0 / total);
        return new Coverage(percent, missing);
    }

    private boolean isCovered(Ingredient ingredient) {
        try {
            return pantry.availableQuantity(ingredient.getName(), ingredient.getUnit()) >= ingredient.getQuantity() - EPSILON;
        } catch (IncompatibleUnitException e) {
            return false;
        }
    }
}
