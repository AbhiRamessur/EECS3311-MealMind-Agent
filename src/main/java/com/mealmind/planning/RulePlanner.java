package com.mealmind.planning;

import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.RecipeScaler;
import com.mealmind.pantry.Pantry;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.ValidationResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a meal plan from the recipe book without any AI: the offline F05 fallback ("switch to the local
 * rule-based fallback generator using cached recipes"). It is the Context of the {@link PlanningStrategy}
 * pattern and is deterministic for a fixed application state.
 *
 * <p><strong>Algorithm.</strong>
 * <ol>
 *   <li>The pool is every recipe with no safety (allergen or diet) violation for the current profile.</li>
 *   <li>Recipes rated 1-2 stars are dropped, unless that would empty the pool.</li>
 *   <li>Slots are filled day by day, in the request's slot order. Each slot takes the pool recipe used
 *       least so far in this plan; ties go to the higher strategy score, then favourites, then the higher
 *       rating (unrated counts as 0), then the lower id.</li>
 *   <li>The recipe is scaled to the profile's household servings and placed as a meal labelled with
 *       the recipe name.</li>
 * </ol>
 *
 * <p><strong>Limitations.</strong> Recipes carry no meal-type tag, so a breakfast slot may receive any
 * safe recipe. Pantry use is not simulated across meals: every recipe is scored against the pantry as it
 * is now. This is the offline fallback; the AI planner will come in a later commit.
 *
 * <p>The generated plan is new and detached: the application's current plan is never touched.
 */
public final class RulePlanner {

    /** Name of every generated plan. */
    public static final String PLAN_NAME = "Weekly Plan";

    private static final double EPSILON = 1e-9;
    private static final int LOW_RATING = 2;

    private final RecipeBook recipes;
    private final Pantry pantry;
    private final ConstraintValidator validator;
    private final RecipePreferences preferences;
    private final UserProfile profile;

    /**
     * Creates a planner over shared application state (read live on every call).
     *
     * @param recipes     the recipes to choose from
     * @param pantry      the pantry used for scoring
     * @param validator   the safety check against {@code profile}
     * @param preferences the user's ratings and favourites
     * @param profile     the profile whose household servings each meal is scaled to
     * @throws NullPointerException if an argument is null
     */
    public RulePlanner(RecipeBook recipes, Pantry pantry, ConstraintValidator validator,
                       RecipePreferences preferences, UserProfile profile) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * Generates a new plan.
     *
     * @param request what to generate
     * @return the plan, its validation and notes
     * @throws IllegalStateException if no recipe is safe for the profile
     * @throws NullPointerException  if {@code request} is null
     */
    public PlanResult generate(PlanRequest request) {
        Objects.requireNonNull(request, "request");
        PlanningStrategy strategy = request.strategy();
        List<String> notes = new ArrayList<>();
        notes.add("Strategy: " + strategy.displayName() + " (rule-based, no AI)");
        List<Recipe> pool = candidatePool(notes);

        ScoringContext context = new ScoringContext(pantry);
        Map<String, Double> scores = new HashMap<>();
        pool.forEach(recipe -> scores.put(recipe.getId(), strategy.score(recipe, context)));
        Map<String, Integer> uses = new HashMap<>();
        Comparator<Recipe> order = Comparator.comparingInt((Recipe r) -> uses.getOrDefault(r.getId(), 0))
                .thenComparing(Comparator.comparingDouble((Recipe r) -> scores.get(r.getId())).reversed())
                .thenComparing((Recipe r) -> !preferences.isFavorite(r.getId()))
                .thenComparing(Comparator.comparingInt((Recipe r) -> preferences.getRating(r.getId()).orElse(0)).reversed())
                .thenComparing(Recipe::getId);

        MealPlan plan = new MealPlan(PLAN_NAME, request.days());
        int servings = profile.getHouseholdServings();
        for (int day = 1; day <= request.days(); day++) {
            for (MealType type : request.slots()) {
                Recipe chosen = pool.stream().min(order).orElseThrow();
                uses.merge(chosen.getId(), 1, Integer::sum);
                Recipe scaled = RecipeScaler.scale(chosen, servings);
                plan.setMeal(day, type, new Meal(type, chosen.getName(), List.of(scaled)));
            }
        }

        int filled = request.days() * request.slots().size();
        if (pool.size() < filled) {
            notes.add("Pool has only " + pool.size() + " safe recipe(s); recipes repeat.");
        }
        if (request.budget() != null) {
            double budget = request.budget();
            String cost = money(plan.getCost());
            notes.add(plan.getCost() > budget + EPSILON
                    ? "Plan cost $" + cost + " exceeds your budget of $" + money(budget)
                    : "Plan cost $" + cost + " is within your budget of $" + money(budget));
        }
        ValidationResult validation = validator.validateMacrosAndAllergens(plan);
        return new PlanResult(plan, validation, notes);
    }

    /** Safe recipes, without low-rated ones when enough others exist; adds a note when ratings changed the pool. */
    private List<Recipe> candidatePool(List<String> notes) {
        List<Recipe> safe = recipes.getAll().stream().filter(r -> validator.validateAgainstBlacklists(r).isSafe()).toList();
        if (safe.isEmpty()) {
            throw new IllegalStateException("No recipes in the recipe book are safe for this profile");
        }
        List<Recipe> kept = safe.stream().filter(r -> !isRatedLow(r)).toList();
        if (kept.isEmpty()) {
            notes.add("All safe recipes are rated low; using them anyway.");
            return safe;
        }
        if (kept.size() < safe.size()) {
            notes.add("Excluded " + (safe.size() - kept.size()) + " recipe(s) you rated 1-2 stars.");
        }
        return kept;
    }

    private boolean isRatedLow(Recipe recipe) {
        return preferences.getRating(recipe.getId()).map(stars -> stars <= LOW_RATING).orElse(false);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
