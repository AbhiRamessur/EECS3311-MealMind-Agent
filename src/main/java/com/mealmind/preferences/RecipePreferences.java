package com.mealmind.preferences;

import com.mealmind.model.ChangeEvent;
import com.mealmind.model.ModelObserver;
import com.mealmind.model.ObservableSubject;
import com.mealmind.model.RecipeBook;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The user's recipe ratings (1 to 5 stars) and favourites (F12).
 *
 * <p>Observer pattern, two roles: it is a ConcreteSubject (the UI and the autosave observer follow
 * it; every effective change notifies once) and a ConcreteObserver of the {@link RecipeBook}: when a
 * recipe disappears from the book, its rating and favourite flag are pruned. Undoing that removal
 * puts the recipe back but does <strong>not</strong> restore its rating (a known limitation).
 */
public final class RecipePreferences extends ObservableSubject implements ModelObserver {

    /** Lowest and highest allowed rating. */
    public static final int MIN_STARS = 1;
    /** Highest allowed rating. */
    public static final int MAX_STARS = 5;

    private final RecipeBook book;
    private final SortedMap<String, Integer> ratings = new TreeMap<>();
    private final SortedSet<String> favorites = new TreeSet<>();

    /**
     * Creates empty preferences and subscribes them to the recipe book for pruning.
     *
     * @param book the recipes that can be rated
     * @throws NullPointerException if {@code book} is null
     */
    public RecipePreferences(RecipeBook book) {
        this.book = Objects.requireNonNull(book, "book");
        book.addObserver(this);
    }

    /** Trims the id and checks the recipe exists. */
    private String existing(String id) {
        Objects.requireNonNull(id, "id");
        String trimmed = id.trim();
        if (trimmed.isEmpty() || !book.contains(trimmed)) {
            throw new NoSuchElementException("No recipe with id '" + trimmed + "'");
        }
        return trimmed;
    }

    private static void checkStars(int stars) {
        if (stars < MIN_STARS || stars > MAX_STARS) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
    }

    /**
     * Rates a recipe.
     *
     * @param id    the recipe id
     * @param stars 1 to 5
     * @return {@code true} if the stored rating changed
     * @throws NoSuchElementException   if the recipe does not exist
     * @throws IllegalArgumentException if {@code stars} is out of range
     */
    public boolean rate(String id, int stars) {
        String key = existing(id);
        checkStars(stars);
        Integer previous = ratings.put(key, stars);
        if (Objects.equals(previous, stars)) {
            return false;
        }
        notifyObservers("Rated " + key + ": " + stars + "/5");
        return true;
    }

    /**
     * Removes a recipe's rating.
     *
     * @param id the recipe id
     * @return {@code true} if a rating was removed
     * @throws NullPointerException if {@code id} is null
     */
    public boolean clearRating(String id) {
        String key = Objects.requireNonNull(id, "id").trim();
        if (ratings.remove(key) == null) {
            return false;
        }
        notifyObservers("Rating removed for " + key);
        return true;
    }

    /**
     * Marks or unmarks a recipe as a favourite.
     *
     * @param id       the recipe id
     * @param favorite whether it should be a favourite
     * @return {@code true} if the flag changed
     * @throws NoSuchElementException if the recipe does not exist
     */
    public boolean setFavorite(String id, boolean favorite) {
        String key = existing(id);
        boolean changed = favorite ? favorites.add(key) : favorites.remove(key);
        if (changed) {
            notifyObservers(favorite ? "Favorited " + key : "Removed " + key + " from favorites");
        }
        return changed;
    }

    /**
     * Returns a recipe's rating.
     *
     * @param id the recipe id
     * @return the stars, or empty if not rated
     * @throws NullPointerException if {@code id} is null
     */
    public Optional<Integer> getRating(String id) {
        return Optional.ofNullable(ratings.get(Objects.requireNonNull(id, "id").trim()));
    }

    /**
     * Returns whether a recipe is a favourite.
     *
     * @param id the recipe id
     * @return {@code true} if favourited
     * @throws NullPointerException if {@code id} is null
     */
    public boolean isFavorite(String id) {
        return favorites.contains(Objects.requireNonNull(id, "id").trim());
    }

    /**
     * Returns the favourite recipe ids.
     *
     * @return an unmodifiable snapshot, sorted by id
     */
    public SortedSet<String> favorites() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(favorites));
    }

    /**
     * Returns every rating.
     *
     * @return an unmodifiable snapshot, sorted by id
     */
    public SortedMap<String, Integer> ratings() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(ratings));
    }

    /**
     * Returns the best-rated recipe ids.
     *
     * @param limit    how many at most, at least 1
     * @param minStars the lowest rating to include, 1 to 5
     * @return ids by rating descending, then id
     * @throws IllegalArgumentException if an argument is out of range
     */
    public List<String> topRated(int limit, int minStars) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        checkStars(minStars);
        return ratings.entrySet().stream()
                .filter(entry -> entry.getValue() >= minStars)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Returns a snapshot for saving.
     *
     * @return the current ratings and favourites
     */
    public PreferenceData toData() {
        return new PreferenceData(ratings, favorites);
    }

    /**
     * Replaces all ratings and favourites. Every rating is validated first, so invalid data changes
     * nothing; then ids that are not in the recipe book are dropped. Notifies at most once.
     *
     * @param data the new state
     * @return the dropped ids, sorted
     * @throws IllegalArgumentException if a rating is out of range (the message names the id)
     * @throws NullPointerException     if {@code data} is null
     */
    public List<String> applyData(PreferenceData data) {
        Objects.requireNonNull(data, "data");
        data.ratings().forEach((id, stars) -> {
            if (stars < MIN_STARS || stars > MAX_STARS) {
                throw new IllegalArgumentException("Rating for " + id + " must be between 1 and 5, got " + stars);
            }
        });
        SortedSet<String> dropped = new TreeSet<>();
        SortedMap<String, Integer> newRatings = new TreeMap<>();
        data.ratings().forEach((id, stars) -> {
            if (book.contains(id)) {
                newRatings.put(id, stars);
            } else {
                dropped.add(id);
            }
        });
        SortedSet<String> newFavorites = new TreeSet<>();
        for (String id : data.favorites()) {
            if (book.contains(id)) {
                newFavorites.add(id);
            } else {
                dropped.add(id);
            }
        }
        boolean changed = !newRatings.equals(ratings) || !newFavorites.equals(favorites);
        ratings.clear();
        ratings.putAll(newRatings);
        favorites.clear();
        favorites.addAll(newFavorites);
        if (changed) {
            notifyObservers("Preferences loaded");
        }
        return new ArrayList<>(dropped);
    }

    /**
     * Called when the recipe book changes: drops ratings and favourites of recipes that no longer
     * exist, notifying once if anything was removed.
     *
     * @param event the book's change
     */
    @Override
    public void onStateChanged(ChangeEvent event) {
        boolean removed = ratings.keySet().removeIf(id -> !book.contains(id));
        removed |= favorites.removeIf(id -> !book.contains(id));
        if (removed) {
            notifyObservers("Removed preferences for deleted recipes");
        }
    }
}
