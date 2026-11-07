package com.mealmind.gui.logic;

import com.mealmind.cli.CliFormat;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the Recipes tab's detail pane with the CLI's {@code recipe show} wording and number formatting
 * ({@link CliFormat}), so both front ends show the same text.
 */
public final class RecipeDetails {

    private RecipeDetails() {
    }

    /**
     * Describes one recipe.
     *
     * @param id        the recipe id
     * @param book      the recipes
     * @param prefs     ratings and favourites
     * @param validator the safety check against the current profile
     * @return the details, or empty if there is no such recipe
     * @throws NullPointerException if a reference argument is null
     */
    public static Optional<RecipeDetail> of(String id, RecipeBook book, RecipePreferences prefs, ConstraintValidator validator) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(prefs, "prefs");
        Objects.requireNonNull(validator, "validator");
        if (id == null) {
            return Optional.empty();
        }
        return book.get(id).map(recipe -> detail(recipe, prefs, validator));
    }

    private static RecipeDetail detail(Recipe recipe, RecipePreferences prefs, ConstraintValidator validator) {
        String id = recipe.getId();
        List<String> ingredients = recipe.getIngredients().stream().map(RecipeDetails::ingredientLine).toList();
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < recipe.getInstructions().size(); i++) {
            steps.add((i + 1) + ". " + recipe.getInstructions().get(i));
        }
        String ratingLine = "Your rating: " + prefs.getRating(id).map(stars -> stars + "/5").orElse("not rated")
                + " | Favorite: " + (prefs.isFavorite(id) ? "yes" : "no");
        List<Violation> problems = validator.validateAgainstBlacklists(recipe).getSafetyViolations();
        String safetyLine = "Profile check: " + (problems.isEmpty() ? "OK"
                : problems.stream().map(Violation::message).collect(Collectors.joining("; ")));
        return new RecipeDetail(id, id + " " + recipe.getName(),
                "Servings: " + recipe.getServings() + " | Prep: " + recipe.getPrepMinutes() + " min", ingredients, steps,
                "Per serving: " + CliFormat.nutrition(recipe, recipe.getServings()), ratingLine, safetyLine,
                id.startsWith("AI"));
    }

    /** "<quantity> <unit> <name>", as in the CLI's ingredient list. */
    static String ingredientLine(Ingredient ingredient) {
        return CliFormat.number(ingredient.getQuantity(), ingredient.getUnit()) + " " + ingredient.getUnit().getSymbol()
                + " " + ingredient.getName();
    }
}
