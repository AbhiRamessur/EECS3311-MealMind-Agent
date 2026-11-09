package com.mealmind.planning;

import com.mealmind.leftovers.ExpiringItem;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Names;
import com.mealmind.model.Recipe;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Prefers recipes that use pantry items about to expire (Stage 1 F10's {@code LeftoverStrategy}): a fourth
 * ConcreteStrategy of the {@link PlanningStrategy} Strategy family.
 *
 * <p>It is <strong>not</strong> listed in {@link PlanningStrategies#all()}, because it needs runtime data
 * (the expiring items) and so cannot be a shared, argument-free instance chosen by id.
 *
 * <p>Score = the sum of the urgency weights of the expiring items the recipe uses &times; 100, plus the
 * pantry coverage percent, so urgency dominates and coverage breaks ties. Matching compares ingredient
 * and item <em>names</em> only; quantities are ignored.
 */
public final class LeftoverStrategy implements PlanningStrategy {

    private static final int MIN_NAME_LENGTH = 3;

    private final List<ExpiringItem> expiring;

    /**
     * Creates the strategy for the given items.
     *
     * @param expiring the expiring items; copied
     * @throws NullPointerException if the list or an element is null
     */
    public LeftoverStrategy(List<ExpiringItem> expiring) {
        this.expiring = List.copyOf(expiring);
    }

    @Override
    public String id() {
        return "leftovers";
    }

    @Override
    public String displayName() {
        return "Use Expiring Items";
    }

    @Override
    public String promptGuidance() {
        return "Use up pantry ingredients that expire soon, most urgent first. "
                + "Prefer recipes that use several expiring ingredients and need few extra purchases.";
    }

    /**
     * Returns whether an ingredient name and a pantry item name refer to the same food. Both must be at
     * least 3 characters. They match when equal, or when the words of one appear consecutively in the
     * other (whole words only: "lentils" matches "red lentils", but "oat" does not match "boat").
     *
     * @param ingredientNormalized the ingredient name, normalized
     * @param itemNormalized       the pantry item name, normalized
     * @return whether they match
     * @throws NullPointerException if an argument is null
     */
    public static boolean matches(String ingredientNormalized, String itemNormalized) {
        Objects.requireNonNull(ingredientNormalized, "ingredientNormalized");
        Objects.requireNonNull(itemNormalized, "itemNormalized");
        if (ingredientNormalized.length() < MIN_NAME_LENGTH || itemNormalized.length() < MIN_NAME_LENGTH) {
            return false;
        }
        if (ingredientNormalized.equals(itemNormalized)) {
            return true;
        }
        List<String> a = words(ingredientNormalized);
        List<String> b = words(itemNormalized);
        return containsRun(a, b) || containsRun(b, a);
    }

    /** The lowercase words of a name, split on anything that is not a letter. */
    private static List<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
                .filter(word -> !word.isEmpty()).toList();
    }

    /** Whether {@code part} appears as a consecutive run inside {@code whole}. */
    private static boolean containsRun(List<String> whole, List<String> part) {
        return !part.isEmpty() && Collections.indexOfSubList(whole, part) >= 0;
    }

    /**
     * Returns the expiring items that at least one ingredient of the recipe matches.
     *
     * @param recipe the recipe
     * @return the items, in the order given to the constructor
     * @throws NullPointerException if {@code recipe} is null
     */
    public List<ExpiringItem> used(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        List<String> ingredients = recipe.getIngredients().stream()
                .map(Ingredient::getName).map(Names::normalize).toList();
        return expiring.stream()
                .filter(item -> ingredients.stream().anyMatch(name -> matches(name, item.normalizedName())))
                .toList();
    }

    @Override
    public double score(Recipe recipe, ScoringContext ctx) {
        int weights = used(recipe).stream().mapToInt(ExpiringItem::weight).sum();
        return weights * 100.0 + PantryCoverage.of(recipe, ctx.pantry()).percent();
    }
}
