package com.mealmind.command;

import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import java.util.Objects;

/**
 * Adds a new recipe to the {@link RecipeBook}, e.g. a generated recipe the user chose to keep (F04/F12).
 * Undo removes it again.
 */
public final class SaveRecipeCommand extends AbstractCommand {

    private final RecipeBook book;
    private final Recipe recipe;

    /**
     * Creates the command.
     *
     * @param book   the receiver
     * @param recipe the recipe to add
     * @throws NullPointerException if an argument is null
     */
    public SaveRecipeCommand(RecipeBook book, Recipe recipe) {
        this.book = Objects.requireNonNull(book, "book");
        this.recipe = Objects.requireNonNull(recipe, "recipe");
    }

    /**
     * Adds the recipe.
     *
     * @throws IllegalStateException if a recipe with the same id already exists; nothing is changed
     */
    @Override
    protected void doExecute() {
        if (book.contains(recipe.getId())) {
            throw new IllegalStateException("A recipe with id " + recipe.getId() + " already exists");
        }
        book.put(recipe);
    }

    @Override
    protected void doUndo() {
        book.remove(recipe.getId());
    }

    @Override
    public String description() {
        return "Save recipe " + recipe.getName() + " (" + recipe.getId() + ")";
    }
}
