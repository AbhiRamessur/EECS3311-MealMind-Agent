package com.mealmind.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Recipe;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecipeRepositoryTest {

    private static final String SOURCE = "test-recipes.json";

    /** One valid recipe; tests replace parts of it to make it invalid. */
    private static final String VALID = """
            {"recipes": [{"id": "R900", "name": "Rice Bowl", "servings": 2, "prepMinutes": 10,
              "instructions": ["Cook", "Serve"],
              "ingredients": [{"name": "Rice", "quantity": 200, "unit": "GRAM", "caloriesTotal": 260,
                               "proteinGTotal": 5, "carbsGTotal": 56, "fatGTotal": 1, "costTotal": 0.5}]}]}
            """;

    private static List<Recipe> load(String json) {
        return RecipeRepository.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), SOURCE);
    }

    private static RepositoryException loadFails(String json) {
        RepositoryException e = assertThrows(RepositoryException.class, () -> load(json));
        assertTrue(e.getMessage().contains(SOURCE), e.getMessage());
        return e;
    }

    @Test
    void sampleResourceHasTenValidRecipes() {
        List<Recipe> recipes = RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES);

        assertEquals(List.of("R101", "R102", "R103", "R104", "R105", "R106", "R107", "R108", "R109", "R110"),
                recipes.stream().map(Recipe::getId).toList());
        for (Recipe r : recipes) {
            assertDoesNotThrow(() -> new Recipe(r.getId(), r.getName(), r.getServings(), r.getPrepMinutes(),
                    r.getInstructions(), r.getIngredients()));
            assertTrue(r.getServings() >= 1 && r.getServings() <= 4, r.getId());
            assertTrue(r.getIngredients().size() >= 3 && r.getIngredients().size() <= 8, r.getId());
            assertTrue(r.getInstructions().size() >= 3 && r.getInstructions().size() <= 6, r.getId());
            assertTrue(r.getCalories() > 0 && r.getCost() > 0, r.getId());
        }
    }

    @Test
    void saveAndLoadRoundTrip(@TempDir Path dir) {
        List<Recipe> original = RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES);
        Path file = dir.resolve("recipes.json");

        RecipeRepository.save(file, original);
        List<Recipe> loaded = RecipeRepository.loadFromFile(file);

        assertEquals(original.size(), loaded.size());
        for (int i = 0; i < original.size(); i++) {
            Recipe a = original.get(i);
            Recipe b = loaded.get(i);
            assertEquals(a.getId(), b.getId());
            assertEquals(a.getName(), b.getName());
            assertEquals(a.getServings(), b.getServings());
            assertEquals(a.getPrepMinutes(), b.getPrepMinutes());
            assertEquals(a.getInstructions(), b.getInstructions());
            assertEquals(a.getIngredients(), b.getIngredients());
            assertEquals(a.getCalories(), b.getCalories(), 1e-9);
            assertEquals(a.getCost(), b.getCost(), 1e-9);
        }
    }

    @Test
    void unitSymbolsAreAccepted() {
        assertEquals(1, load(VALID.replace("\"GRAM\"", "\"g\"")).size());
    }

    @Test
    void invalidJsonIsRejected() {
        loadFails("{\"recipes\": [ {\"id\": ");
    }

    @Test
    void missingRequiredFieldNamesRecipeAndField() {
        RepositoryException e = loadFails(VALID.replace("\"servings\": 2, ", ""));
        assertTrue(e.getMessage().contains("R900") && e.getMessage().contains("servings"), e.getMessage());
    }

    @Test
    void unknownFieldIsRejected() {
        RepositoryException e = loadFails(VALID.replace("\"servings\"", "\"servingz\""));
        assertTrue(e.getMessage().contains("servingz"), e.getMessage());
    }

    @Test
    void unknownUnitNamesRecipe() {
        RepositoryException e = loadFails(VALID.replace("\"GRAM\"", "\"bushel\""));
        assertTrue(e.getMessage().contains("R900") && e.getMessage().contains("bushel"), e.getMessage());
    }

    @Test
    void negativeQuantityNamesRecipe() {
        RepositoryException e = loadFails(VALID.replace("\"quantity\": 200", "\"quantity\": -200"));
        assertTrue(e.getMessage().contains("R900"), e.getMessage());
    }

    @Test
    void duplicateIdIsRejected() {
        String recipe = VALID.substring(VALID.indexOf("{\"id\""), VALID.lastIndexOf(']'));
        RepositoryException e = loadFails("{\"recipes\": [" + recipe + ", " + recipe + "]}");
        assertTrue(e.getMessage().contains("R900") && e.getMessage().contains("duplicate"), e.getMessage());
    }

    @Test
    void emptyRecipesArrayIsRejected() {
        loadFails("{\"recipes\": []}");
    }

    @Test
    void missingFileOrResourceIsRejected(@TempDir Path dir) {
        assertThrows(RepositoryException.class, () -> RecipeRepository.loadFromFile(dir.resolve("none.json")));
        assertThrows(RepositoryException.class, () -> RecipeRepository.loadFromResource("/recipes/none.json"));
    }

    @Test
    void saveCreatesParentDirectoriesAndLeavesNoTempFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/deeper/recipes.json");

        RecipeRepository.save(file, load(VALID));

        assertTrue(Files.exists(file));
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(List.of(file.getFileName()), files.map(Path::getFileName).toList());
        }
        assertTrue(Files.readString(file).contains("\"unit\" : \"GRAM\""));
    }
}
