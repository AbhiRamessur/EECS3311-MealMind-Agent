package com.mealmind.command;

import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Removes a recipe from the {@link RecipeBook}. Undo puts the exact removed recipe back.
 */
public final class RemoveRecipeCommand extends AbstractCommand {

    private final RecipeBook book;
    private final String id;
    private Recipe removed;

    /**
     * Creates the command.
     *
     * @param book the receiver
     * @param id   the id of the recipe to remove
     * @throws NullPointerException if an argument is null
     */
    public RemoveRecipeCommand(RecipeBook book, String id) {
        this.book = Objects.requireNonNull(book, "book");
        this.id = Objects.requireNonNull(id, "id").trim();
    }

    /**
     * Removes the recipe.
     *
     * @throws NoSuchElementException if there is no such recipe; nothing is changed
     */
    @Override
    protected void doExecute() {
        removed = book.remove(id).orElseThrow(() -> new NoSuchElementException("No recipe with id '" + id + "'"));
    }

    @Override
    protected void doUndo() {
        book.put(removed);
    }

    @Override
    public String description() {
        return "Remove recipe " + id;
    }
}
