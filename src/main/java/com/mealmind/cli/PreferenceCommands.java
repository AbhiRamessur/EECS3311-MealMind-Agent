package com.mealmind.cli;

import com.mealmind.app.ApplicationContext;
import com.mealmind.command.RemoveRecipeCommand;
import com.mealmind.model.Recipe;
import com.mealmind.preferences.RecipePreferences;
import java.io.PrintStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The F12 recipe commands: {@code recipe rate}, {@code favorite}, {@code unfavorite}, {@code save}
 * (keep the pending AI recipe) and {@code remove} (delete a saved recipe). Saving and removing go
 * through the command history, so {@code undo} works; ratings and favourites are preferences and are
 * changed directly. Output is ASCII only (ratings are written as "4/5").
 */
final class PreferenceCommands {

    static final String RATE_USAGE = "recipe rate --id <id> --stars <1-5|none>";
    static final String FAVORITE_USAGE = "recipe favorite --id <id>";
    static final String UNFAVORITE_USAGE = "recipe unfavorite --id <id>";
    static final String REMOVE_USAGE = "recipe remove <id>";

    private final ApplicationContext ctx;
    private final PrintStream out;

    PreferenceCommands(ApplicationContext ctx, PrintStream out) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.out = Objects.requireNonNull(out, "out");
    }

    private Recipe recipe(String id) {
        return ctx.recipeBook().get(id.trim())
                .orElseThrow(() -> new NoSuchElementException("No recipe with id '" + id.trim() + "'"));
    }

    /** {@code recipe rate --id <id> --stars <1-5|none>}. */
    void rate(List<String> args) {
        CliArgs parsed = CliArgs.parse(args, Set.of("id", "stars"), RATE_USAGE).expectPositional(0, 0, RATE_USAGE);
        Recipe recipe = recipe(parsed.requireFlag("id", RATE_USAGE));
        String stars = parsed.requireFlag("stars", RATE_USAGE);
        RecipePreferences preferences = ctx.preferences();
        if (stars.equalsIgnoreCase("none")) {
            out.println(preferences.clearRating(recipe.getId())
                    ? "Rating removed for " + recipe.getName() + "." : recipe.getName() + " was not rated.");
            return;
        }
        int value = CliArgs.parseInt(stars, RATE_USAGE);
        preferences.rate(recipe.getId(), value);
        out.println("Rated " + recipe.getName() + ": " + value + "/5.");
    }

    /** {@code recipe favorite --id <id>} or {@code recipe unfavorite --id <id>}. */
    void favorite(List<String> args, boolean favorite) {
        String usage = favorite ? FAVORITE_USAGE : UNFAVORITE_USAGE;
        CliArgs parsed = CliArgs.parse(args, Set.of("id"), usage).expectPositional(0, 0, usage);
        Recipe recipe = recipe(parsed.requireFlag("id", usage));
        boolean changed = ctx.preferences().setFavorite(recipe.getId(), favorite);
        if (favorite) {
            out.println(changed ? "Added " + recipe.getName() + " to favorites." : "Already a favorite.");
        } else {
            out.println(changed ? "Removed " + recipe.getName() + " from favorites." : "Not a favorite.");
        }
    }

    /** {@code recipe save}: keeps the pending AI recipe (re-checked for safety first). */
    void save() {
        Recipe saved = ctx.agent().savePendingRecipe(ctx.history());
        out.println("Saved " + saved.getName() + " as " + saved.getId() + ".");
        out.println("Use 'undo' to remove it.");
    }

    /** {@code recipe remove <id>}: deletes a saved recipe; bundled sample recipes are protected. */
    void remove(List<String> args) {
        String id = CliArgs.parse(args, Set.of(), REMOVE_USAGE).expectPositional(1, 1, REMOVE_USAGE).positional().get(0).trim();
        if (ctx.isBundledRecipe(id)) {
            throw new IllegalArgumentException(id + " is a bundled sample recipe and cannot be removed");
        }
        Recipe recipe = recipe(id);
        ctx.history().pushAndExecute(new RemoveRecipeCommand(ctx.recipeBook(), recipe.getId()));
        out.println("Removed " + recipe.getName() + ". Its rating and favorite flag were dropped.");
    }

    /** The extra columns for {@code recipe list}: rating ("4/5" or "-") and "fav" for favourites. */
    String listSuffix(String id) {
        Optional<Integer> rating = ctx.preferences().getRating(id);
        return "  " + rating.map(stars -> stars + "/5").orElse("-") + (ctx.preferences().isFavorite(id) ? "  fav" : "");
    }

    /** The {@code recipe show} line, e.g. "Your rating: 4/5 | Favorite: yes". */
    String showLine(String id) {
        return "Your rating: " + ctx.preferences().getRating(id).map(stars -> stars + "/5").orElse("not rated")
                + " | Favorite: " + (ctx.preferences().isFavorite(id) ? "yes" : "no");
    }
}
