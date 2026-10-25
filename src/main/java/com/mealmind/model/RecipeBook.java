package com.mealmind.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory store of {@link Recipe}s keyed by id. Because recipes are immutable, changing one
 * (for example scaling it) means {@linkplain #put replacing} it here; this class is the receiver
 * for recipe commands such as {@code ScaleRecipeCommand}.
 *
 * <p>A ConcreteSubject in the Observer pattern: observers are notified once after every
 * successful change. Ids are matched exactly ({@link Recipe} already trims them).
 *
 * <p>Persistence and ratings will be layered on in a later commit.
 */
public final class RecipeBook extends ObservableSubject {

    private final Map<String, Recipe> recipes = new HashMap<>();

    /**
     * Stores a recipe, replacing any recipe with the same id, then notifies observers.
     *
     * @param recipe the recipe to store
     * @return the recipe previously stored under that id, or empty (used for undo)
     * @throws NullPointerException if {@code recipe} is null
     */
    public Optional<Recipe> put(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        Optional<Recipe> previous = Optional.ofNullable(recipes.put(recipe.getId(), recipe));
        notifyObservers("Stored recipe " + recipe.getId());
        return previous;
    }

    /**
     * Removes a recipe. Observers are notified only if a recipe was actually removed.
     *
     * @param id the recipe id
     * @return the removed recipe, or empty if there was none
     * @throws NullPointerException if {@code id} is null
     */
    public Optional<Recipe> remove(String id) {
        Optional<Recipe> removed = Optional.ofNullable(recipes.remove(Objects.requireNonNull(id, "id")));
        removed.ifPresent(recipe -> notifyObservers("Removed recipe " + recipe.getId()));
        return removed;
    }

    /**
     * Looks up a recipe by id.
     *
     * @param id the recipe id
     * @return the recipe, or empty if there is none
     * @throws NullPointerException if {@code id} is null
     */
    public Optional<Recipe> get(String id) {
        return Optional.ofNullable(recipes.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Returns all recipes.
     *
     * @return an unmodifiable list sorted by id
     */
    public List<Recipe> getAll() {
        return recipes.values().stream().sorted(Comparator.comparing(Recipe::getId)).toList();
    }

    /**
     * Returns the number of stored recipes.
     *
     * @return the size
     */
    public int size() {
        return recipes.size();
    }

    /**
     * Returns whether a recipe with the given id is stored.
     *
     * @param id the recipe id
     * @return {@code true} if present
     * @throws NullPointerException if {@code id} is null
     */
    public boolean contains(String id) {
        return recipes.containsKey(Objects.requireNonNull(id, "id"));
    }
}
