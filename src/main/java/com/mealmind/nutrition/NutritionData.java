package com.mealmind.nutrition;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Names;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Decides whether an ingredient's nutrition is missing: Stage 1's "Nutritional Data Unavailable" rule.
 *
 * <p>An {@link Ingredient} always carries numbers, so "unknown" is a documented <strong>heuristic</strong>:
 * calories, protein, carbs and fat are all exactly 0 and the name is not a genuinely zero-nutrition
 * item. Those items are matched by whole words after {@link Names#normalize}, the same style as the
 * allergen keywords: a name is zero-nutrition when it contains one of the phrases {@code water},
 * {@code ice}, {@code salt}, {@code pepper}, {@code black pepper}, {@code white pepper},
 * {@code baking soda}, {@code baking powder} or {@code vinegar} as consecutive words, or the word
 * {@code spice(s)} or {@code seasoning(s)}. So "Pinch of salt" is known zero, while "Saltine crackers"
 * with zero values is unknown.
 */
public final class NutritionData {

    private static final List<List<String>> ZERO_PHRASES = List.of(
            List.of("water"), List.of("ice"), List.of("salt"), List.of("pepper"), List.of("black", "pepper"),
            List.of("white", "pepper"), List.of("baking", "soda"), List.of("baking", "powder"), List.of("vinegar"));
    private static final Set<String> ZERO_WORDS = Set.of("spice", "spices", "seasoning", "seasonings");

    private NutritionData() {
    }

    /**
     * Returns whether {@code ingredient} looks like it has no nutrition data.
     *
     * @param ingredient the ingredient to check
     * @return {@code true} if calories and all macros are 0 and the name is not a known zero-nutrition item
     * @throws NullPointerException if {@code ingredient} is null
     */
    public static boolean isUnknown(Ingredient ingredient) {
        Objects.requireNonNull(ingredient, "ingredient");
        boolean allZero = ingredient.getCalories() == 0 && ingredient.getProteinG() == 0
                && ingredient.getCarbsG() == 0 && ingredient.getFatG() == 0;
        return allZero && !isKnownZero(ingredient.getName());
    }

    private static boolean isKnownZero(String name) {
        List<String> words = Arrays.stream(Names.normalize(name).split("[^\\p{L}]+"))
                .filter(word -> !word.isEmpty()).toList();
        return words.stream().anyMatch(ZERO_WORDS::contains)
                || ZERO_PHRASES.stream().anyMatch(phrase -> Collections.indexOfSubList(words, phrase) >= 0);
    }
}
