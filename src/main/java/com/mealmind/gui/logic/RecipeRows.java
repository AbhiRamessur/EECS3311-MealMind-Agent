package com.mealmind.gui.logic;

import com.mealmind.model.Names;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Builds the Recipes tab's rows.
 *
 * <p>Unsafe recipes are <strong>listed but flagged</strong> ({@code safe = false}), so the user can see why a
 * recipe is never suggested. This differs on purpose from the agent tools, which hide unsafe recipes from
 * the AI so that it can never propose them.
 */
public final class RecipeRows {

    /** Shown for a recipe that breaks the profile's diet or allergen rules. */
    public static final String NOT_SAFE = "Not safe for your profile";

    private RecipeRows() {
    }

    /**
     * Returns the rows, sorted by id.
     *
     * @param book          the recipes
     * @param prefs         ratings and favourites
     * @param validator     the safety check against the current profile
     * @param isBundled     tells whether an id is a bundled sample recipe
     * @param favoritesOnly keep only favourites
     * @param filterText    text to find in the id, name or an ingredient name (ignoring case); blank for none
     * @return the rows
     * @throws NullPointerException if a reference argument is null
     */
    public static List<RecipeRow> of(RecipeBook book, RecipePreferences prefs, ConstraintValidator validator,
                                     Predicate<String> isBundled, boolean favoritesOnly, String filterText) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(prefs, "prefs");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(isBundled, "isBundled");
        String filter = filterText == null ? "" : Names.normalize(filterText);
        return book.getAll().stream()
                .filter(recipe -> !favoritesOnly || prefs.isFavorite(recipe.getId()))
                .filter(recipe -> filter.isEmpty() || matches(recipe, filter))
                .map(recipe -> row(recipe, prefs, validator, isBundled))
                .toList();
    }

    private static boolean matches(Recipe recipe, String filter) {
        return Names.normalize(recipe.getId()).contains(filter) || Names.normalize(recipe.getName()).contains(filter)
                || recipe.getIngredients().stream().anyMatch(i -> i.normalizedName().contains(filter));
    }

    private static RecipeRow row(Recipe recipe, RecipePreferences prefs, ConstraintValidator validator,
                                 Predicate<String> isBundled) {
        String id = recipe.getId();
        boolean safe = validator.isSafe(recipe);
        return new RecipeRow(id, recipe.getName(), String.valueOf(recipe.getServings()), recipe.getPrepMinutes() + " min",
                String.format(Locale.ROOT, "%.0f", recipe.caloriesPerServing()),
                prefs.getRating(id).map(stars -> stars + "/5").orElse("-"), prefs.isFavorite(id), isBundled.test(id),
                safe, safe ? "" : NOT_SAFE);
    }
}
