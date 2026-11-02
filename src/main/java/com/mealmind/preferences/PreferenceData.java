package com.mealmind.preferences;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable snapshot of {@link RecipePreferences}, used for saving and loading. Values are not
 * validated here; {@link RecipePreferences#applyData} validates them.
 *
 * @param ratings   recipe id to stars
 * @param favorites ids of favourite recipes
 */
public record PreferenceData(Map<String, Integer> ratings, Set<String> favorites) {

    /** No ratings and no favourites. */
    public static final PreferenceData EMPTY = new PreferenceData(Map.of(), Set.of());

    /**
     * Copies both collections.
     *
     * @throws NullPointerException if a collection or an entry is null
     */
    public PreferenceData {
        ratings = Map.copyOf(Objects.requireNonNull(ratings, "ratings"));
        favorites = Set.copyOf(Objects.requireNonNull(favorites, "favorites"));
    }
}
