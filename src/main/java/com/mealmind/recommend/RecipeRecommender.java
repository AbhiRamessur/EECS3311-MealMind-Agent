package com.mealmind.recommend;

import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.pantry.Pantry;
import com.mealmind.planning.PantryCoverage;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.validation.ConstraintValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Recommends recipes deterministically (the F03 base that works without AI).
 *
 * <p><strong>Algorithm.</strong>
 * <ol>
 *   <li>Only recipes with no safety (allergen or diet) violation are considered.</li>
 *   <li>Recipes rated 1-2 stars are dropped, unless that would leave nothing.</li>
 *   <li>The prep-time limit is applied; if nothing fits, it is relaxed (Stage 1: relax non-safety
 *       constraints and tell the user) and the quickest recipes come first.</li>
 *   <li>Score: with {@code pantryFirst}, coverage % x 100 + preference bonus; otherwise preference
 *       bonus x 100 + coverage %. Preference bonus = 5 for a favourite plus (rating - 3) when rated.
 *       Ties: fewer missing ingredients, then id. With an empty pantry only the bonus, then the id, count.</li>
 * </ol>
 * Safety is never relaxed.
 */
public final class RecipeRecommender {

    private static final int LOW_RATING = 2;
    private static final int NEUTRAL_RATING = 3;
    private static final int FAVORITE_BONUS = 5;
    private static final int MAX_MISSING_IN_REASON = 3;

    private final RecipeBook recipes;
    private final Pantry pantry;
    private final ConstraintValidator validator;
    private final RecipePreferences preferences;

    /** A candidate with its computed figures. */
    private record Candidate(Recipe recipe, PantryCoverage.Result coverage, int bonus) {
    }

    /**
     * Creates a recommender over shared application state (read live on every call).
     *
     * @param recipes     the recipes to choose from
     * @param pantry      the pantry used for coverage
     * @param validator   the safety check
     * @param preferences the user's ratings and favourites
     * @throws NullPointerException if an argument is null
     */
    public RecipeRecommender(RecipeBook recipes, Pantry pantry, ConstraintValidator validator,
                             RecipePreferences preferences) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /**
     * Recommends recipes.
     *
     * @param request what to recommend
     * @return the recommendations and notes
     * @throws IllegalStateException if no recipe is safe for the profile
     * @throws NullPointerException  if {@code request} is null
     */
    public RecommendationResult recommend(RecommendationRequest request) {
        Objects.requireNonNull(request, "request");
        List<String> notes = new ArrayList<>();
        List<Recipe> pool = recipes.getAll().stream().filter(r -> validator.validateAgainstBlacklists(r).isSafe()).toList();
        if (pool.isEmpty()) {
            throw new IllegalStateException("No recipes in the recipe book are safe for this profile");
        }
        List<Recipe> liked = pool.stream().filter(r -> !isRatedLow(r)).toList();
        if (!liked.isEmpty() && liked.size() < pool.size()) {
            notes.add("Excluded " + (pool.size() - liked.size()) + " recipe(s) you rated 1-2 stars.");
            pool = liked;
        }

        boolean relaxed = false;
        Integer maxPrep = request.maxPrepMinutes();
        if (maxPrep != null) {
            List<Recipe> quick = pool.stream().filter(r -> r.getPrepMinutes() <= maxPrep).toList();
            if (quick.isEmpty()) {
                relaxed = true;
                notes.add("No recipe fits " + maxPrep + " minutes; showing the closest matches instead.");
            } else {
                pool = quick;
            }
        }
        boolean emptyPantry = pantry.isEmpty();
        if (emptyPantry) {
            notes.add("Pantry is empty; ranking uses your ratings and favorites only.");
        }

        List<Candidate> candidates = new ArrayList<>(pool.stream().map(this::candidate).toList());
        candidates.sort(order(request.pantryFirst(), relaxed, emptyPantry));
        List<Recommendation> items = candidates.stream().limit(request.limit()).map(this::recommendation).toList();
        return new RecommendationResult(items, notes);
    }

    private static Comparator<Candidate> order(boolean pantryFirst, boolean relaxed, boolean emptyPantry) {
        Comparator<Candidate> byScore = emptyPantry
                ? Comparator.comparingInt((Candidate c) -> c.bonus()).reversed()
                : Comparator.comparingInt((Candidate c) -> score(c, pantryFirst)).reversed()
                        .thenComparingInt(c -> c.coverage().missing().size());
        Comparator<Candidate> order = byScore.thenComparing(c -> c.recipe().getId());
        return relaxed ? Comparator.comparingInt((Candidate c) -> c.recipe().getPrepMinutes()).thenComparing(order) : order;
    }

    private static int score(Candidate c, boolean pantryFirst) {
        int coverage = c.coverage().percent();
        return pantryFirst ? coverage * 100 + c.bonus() : c.bonus() * 100 + coverage;
    }

    private Candidate candidate(Recipe recipe) {
        boolean favorite = preferences.isFavorite(recipe.getId());
        int bonus = (favorite ? FAVORITE_BONUS : 0)
                + preferences.getRating(recipe.getId()).map(stars -> stars - NEUTRAL_RATING).orElse(0);
        return new Candidate(recipe, PantryCoverage.of(recipe, pantry), bonus);
    }

    private Recommendation recommendation(Candidate c) {
        String id = c.recipe().getId();
        boolean favorite = preferences.isFavorite(id);
        Integer rating = preferences.getRating(id).orElse(null);
        return new Recommendation(c.recipe(), c.coverage().percent(), c.coverage().missing(), favorite, rating,
                reason(c, favorite, rating));
    }

    /** E.g. "Pantry covers 50% of the recipe; missing: Onion, Garlic, Salt and 2 more; favorite; rated 4/5; 20 min". */
    private static String reason(Candidate c, boolean favorite, Integer rating) {
        StringBuilder reason = new StringBuilder("Pantry covers ").append(c.coverage().percent()).append("% of the recipe");
        List<String> missing = c.coverage().missing();
        if (!missing.isEmpty()) {
            reason.append("; missing: ")
                    .append(String.join(", ", missing.subList(0, Math.min(MAX_MISSING_IN_REASON, missing.size()))));
            if (missing.size() > MAX_MISSING_IN_REASON) {
                reason.append(" and ").append(missing.size() - MAX_MISSING_IN_REASON).append(" more");
            }
        }
        if (favorite) {
            reason.append("; favorite");
        }
        if (rating != null) {
            reason.append("; rated ").append(rating).append("/5");
        }
        return reason.append("; ").append(c.recipe().getPrepMinutes()).append(" min").toString();
    }

    private boolean isRatedLow(Recipe recipe) {
        return preferences.getRating(recipe.getId()).map(stars -> stars <= LOW_RATING).orElse(false);
    }
}
