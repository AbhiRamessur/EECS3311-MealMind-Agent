package com.mealmind.command;

import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.RecipeScaler;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Scales a recipe in the {@link RecipeBook} to a new number of servings (F08 / UC08). Recipes are
 * immutable, so execute stores a scaled copy and undo puts the original instance back.
 */
public final class ScaleRecipeCommand extends AbstractCommand {

    private final RecipeBook book;
    private final String recipeId;
    private final int targetServings;
    private Recipe original;

    /**
     * Creates the command.
     *
     * @param book           the receiver
     * @param recipeId       the id of the recipe to scale
     * @param targetServings the new number of servings, {@code >= 1}
     * @throws NullPointerException     if {@code book} or {@code recipeId} is null
     * @throws IllegalArgumentException if {@code targetServings < 1}
     */
    public ScaleRecipeCommand(RecipeBook book, String recipeId, int targetServings) {
        this.book = Objects.requireNonNull(book, "book");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        if (targetServings < 1) {
            throw new IllegalArgumentException("Target servings must be at least 1, got " + targetServings);
        }
        this.targetServings = targetServings;
    }

    /**
     * Replaces the recipe with its scaled copy.
     *
     * @throws NoSuchElementException if the book has no recipe with this id; nothing is changed
     */
    @Override
    protected void doExecute() {
        Recipe current = book.get(recipeId)
                .orElseThrow(() -> new NoSuchElementException("No recipe with id '" + recipeId + "'"));
        book.put(RecipeScaler.scale(current, targetServings));
        original = current;
    }

    @Override
    protected void doUndo() {
        book.put(original);
    }

    @Override
    public String description() {
        String name = book.get(recipeId).map(Recipe::getName).orElse(recipeId);
        return "Scale " + name + " to " + targetServings + " servings";
    }
}
