package com.mealmind.command;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Replaces one ingredient of a recipe in the {@link RecipeBook} (F07 / UC07). The recipe is immutable, so a
 * new {@link Recipe} with the same id, name, servings, prep time and instructions is put into the book;
 * undo puts the previous recipe instance back. The steps are not rewritten.
 *
 * <p>Checking that the replacement is safe for the user is the caller's responsibility
 * ({@code SubstitutionApplier} does it immediately before running this command).
 */
public final class SubstituteIngredientCommand extends AbstractCommand {

    private final RecipeBook book;
    private final String recipeId;
    private final int ingredientIndex;
    private final Ingredient replacement;
    private Recipe previous;
    private String originalName;
    private String recipeName;

    /**
     * Creates the command.
     *
     * @param book            the receiver
     * @param recipeId        the recipe to change
     * @param ingredientIndex the position of the ingredient to replace (0-based)
     * @param replacement     the new ingredient
     * @throws NullPointerException if {@code book}, {@code recipeId} or {@code replacement} is null
     */
    public SubstituteIngredientCommand(RecipeBook book, String recipeId, int ingredientIndex, Ingredient replacement) {
        this.book = Objects.requireNonNull(book, "book");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.ingredientIndex = ingredientIndex;
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    @Override
    protected void doExecute() {
        Recipe current = book.get(recipeId)
                .orElseThrow(() -> new NoSuchElementException("No recipe with id '" + recipeId + "'"));
        List<Ingredient> ingredients = new ArrayList<>(current.getIngredients());
        if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) {
            throw new IllegalArgumentException("Ingredient index " + ingredientIndex + " is outside 0 to "
                    + (ingredients.size() - 1) + " for recipe " + recipeId);
        }
        String replaced = ingredients.get(ingredientIndex).getName();
        ingredients.set(ingredientIndex, replacement);
        Recipe updated = new Recipe(current.getId(), current.getName(), current.getServings(), current.getPrepMinutes(),
                current.getInstructions(), ingredients);
        book.put(updated);
        previous = current;
        originalName = replaced;
        recipeName = current.getName();
    }

    @Override
    protected void doUndo() {
        book.put(previous);
    }

    @Override
    public String description() {
        if (originalName == null) {
            return "Substitute ingredient " + (ingredientIndex + 1) + " of " + recipeId + " with " + replacement.getName();
        }
        return "Substitute " + originalName + " with " + replacement.getName() + " in " + recipeName;
    }
}
