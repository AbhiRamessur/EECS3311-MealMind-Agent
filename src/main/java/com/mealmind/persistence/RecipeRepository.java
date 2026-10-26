package com.mealmind.persistence;

import static com.mealmind.persistence.JsonFiles.required;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads and writes recipes as JSON.
 *
 * <p>Schema:
 * <pre>
 * {"recipes": [{"id": "R101", "name": "...", "servings": 2, "prepMinutes": 15,
 *               "instructions": ["..."],
 *               "ingredients": [{"name": "...", "quantity": 300, "unit": "GRAM",
 *                                "caloriesTotal": 495, "proteinGTotal": 93, "carbsGTotal": 0,
 *                                "fatGTotal": 11, "costTotal": 4.5}]}]}
 * </pre>
 * Nutrition and cost are <strong>totals for the stated quantity</strong>, exactly as in
 * {@link Ingredient}. {@code unit} accepts anything {@link Unit#parse} does ({@code "GRAM"} or
 * {@code "g"}); {@link #save} writes the enum name. Unknown fields are rejected so typos are caught.
 * Every value goes through the {@link Recipe} and {@link Ingredient} constructors, so invalid data is
 * reported, never trusted. Any problem is a {@link RepositoryException} naming the source and recipe.
 *
 * <p>The bundled {@code /recipes/sample-recipes.json} is <strong>sample data</strong> with
 * approximate nutrition and prices for the demo and tests; it is not an authoritative nutrition source.
 * The same schema and {@link #save} will be used for AI-generated recipes.
 */
public final class RecipeRepository {

    /** Classpath location of the bundled sample recipes. */
    public static final String SAMPLE_RECIPES = "/recipes/sample-recipes.json";

    private record RecipeFileDto(List<RecipeDto> recipes) {
    }

    private record RecipeDto(String id, String name, Integer servings, Integer prepMinutes,
                             List<String> instructions, List<IngredientDto> ingredients) {
    }

    private record IngredientDto(String name, Double quantity, String unit, Double caloriesTotal,
                                 Double proteinGTotal, Double carbsGTotal, Double fatGTotal, Double costTotal) {
    }

    private RecipeRepository() {
    }

    /**
     * Parses recipes from a JSON stream.
     *
     * @param in         the JSON input
     * @param sourceName a name for the input, used only in error messages
     * @return the recipes, in file order
     * @throws RepositoryException if the JSON is malformed or any recipe is invalid
     */
    public static List<Recipe> load(InputStream in, String sourceName) {
        Objects.requireNonNull(in, "in");
        RecipeFileDto file;
        try {
            file = JsonFiles.MAPPER.readValue(in, RecipeFileDto.class);
        } catch (JsonProcessingException e) {
            throw new RepositoryException(sourceName + ": invalid recipe JSON: " + JsonFiles.describe(e), e);
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + sourceName + ": " + e.getMessage(), e);
        }
        if (file == null || file.recipes() == null || file.recipes().isEmpty()) {
            throw new RepositoryException(sourceName + ": \"recipes\" must be a non-empty array");
        }

        List<Recipe> recipes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < file.recipes().size(); i++) {
            RecipeDto dto = file.recipes().get(i);
            String label = (dto != null && dto.id() != null) ? "recipe " + dto.id() : "recipe #" + (i + 1);
            Recipe recipe;
            try {
                recipe = toRecipe(required(dto, "recipe"));
            } catch (IllegalArgumentException e) {
                throw new RepositoryException(sourceName + ", " + label + ": " + e.getMessage(), e);
            }
            if (!ids.add(recipe.getId())) {
                throw new RepositoryException(sourceName + ", " + label + ": duplicate recipe id");
            }
            recipes.add(recipe);
        }
        return List.copyOf(recipes);
    }

    private static Recipe toRecipe(RecipeDto dto) {
        List<IngredientDto> ingredientDtos = required(dto.ingredients(), "ingredients");
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < ingredientDtos.size(); i++) {
            try {
                ingredients.add(toIngredient(required(ingredientDtos.get(i), "ingredient")));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("ingredient #" + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new Recipe(required(dto.id(), "id"), required(dto.name(), "name"),
                required(dto.servings(), "servings"), required(dto.prepMinutes(), "prepMinutes"),
                required(dto.instructions(), "instructions"), ingredients);
    }

    private static Ingredient toIngredient(IngredientDto dto) {
        return new Ingredient(required(dto.name(), "name"), required(dto.quantity(), "quantity"),
                Unit.parse(required(dto.unit(), "unit")), required(dto.caloriesTotal(), "caloriesTotal"),
                required(dto.proteinGTotal(), "proteinGTotal"), required(dto.carbsGTotal(), "carbsGTotal"),
                required(dto.fatGTotal(), "fatGTotal"), required(dto.costTotal(), "costTotal"));
    }

    /**
     * Loads recipes from the classpath.
     *
     * @param resourcePath an absolute classpath path such as {@link #SAMPLE_RECIPES}
     * @return the recipes
     * @throws RepositoryException if the resource is missing or invalid
     */
    public static List<Recipe> loadFromResource(String resourcePath) {
        try (InputStream in = RecipeRepository.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RepositoryException("Recipe resource not found: " + resourcePath);
            }
            return load(in, resourcePath);
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Loads recipes from a file.
     *
     * @param file the JSON file
     * @return the recipes
     * @throws RepositoryException if the file is missing or invalid
     */
    public static List<Recipe> loadFromFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in, file.toString());
        } catch (NoSuchFileException e) {
            throw new RepositoryException("Recipe file not found: " + file, e);
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Writes recipes to a file in the schema above, atomically, creating parent directories.
     *
     * @param file    the destination
     * @param recipes the recipes to write
     * @throws RepositoryException if the file cannot be written
     */
    public static void save(Path file, List<Recipe> recipes) {
        List<RecipeDto> dtos = recipes.stream().map(RecipeRepository::toDto).toList();
        JsonFiles.writeAtomically(file, new RecipeFileDto(dtos));
    }

    private static RecipeDto toDto(Recipe recipe) {
        List<IngredientDto> ingredients = recipe.getIngredients().stream()
                .map(i -> new IngredientDto(i.getName(), i.getQuantity(), i.getUnit().name(), i.getCalories(),
                        i.getProteinG(), i.getCarbsG(), i.getFatG(), i.getCost()))
                .toList();
        return new RecipeDto(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getPrepMinutes(),
                recipe.getInstructions(), ingredients);
    }
}
