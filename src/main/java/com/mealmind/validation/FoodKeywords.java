package com.mealmind.validation;

import com.mealmind.model.Names;
import com.mealmind.profile.DietType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Documented keyword data used by the {@link ConstraintValidator}: the allergen catalog, the diet
 * exclusion lists and the false-positive exceptions, plus the word-matching rules. This is a
 * heuristic over ingredient <em>names</em>, not a food database.
 *
 * <p><strong>Matching rules.</strong> Text is normalized ({@link Names#normalize}) and split into
 * words on every non-letter character, so "Peanut-free" is the two words "peanut" and "free" (and is
 * therefore flagged by a peanut allergy: the check prefers false positives). A term of one or more
 * words matches when its words appear consecutively in the ingredient's words. Two words match when
 * they are equal or one is the other plus "s" or "es" ("peanut"/"peanuts", "tomato"/"tomatoes").
 * Only whole words match: "nut" does not match "nutmeg", and "pasta" does not match "pastry".
 *
 * <p><strong>Exceptions.</strong> Before checking the dairy group (an allergy or the vegan rule),
 * phrases such as "peanut butter" or "coconut milk" are removed from the ingredient name, and before
 * checking the gluten group, flours such as "rice flour" are removed. The exceptions apply only to
 * those groups, so "almond milk" still triggers a nut allergy and "peanut butter" a peanut allergy.
 */
final class FoodKeywords {

    static final List<String> PEANUT = List.of("peanut", "groundnut");
    static final List<String> TREE_NUTS = List.of("almond", "walnut", "cashew", "pecan", "pistachio", "hazelnut",
            "macadamia", "brazil nut", "pine nut");
    static final List<String> DAIRY = List.of("milk", "cheese", "butter", "cream", "yogurt", "yoghurt", "whey",
            "casein", "ghee", "kefir", "ice cream");
    static final List<String> GLUTEN = List.of("wheat", "flour", "bread", "breadcrumb", "pasta", "couscous", "barley",
            "rye", "semolina", "seitan", "noodle", "noodles", "tortilla");
    static final List<String> EGG = List.of("egg", "mayonnaise", "mayo");
    static final List<String> SHELLFISH = List.of("shrimp", "prawn", "crab", "lobster", "clam", "mussel", "oyster",
            "scallop", "crayfish");
    static final List<String> FISH = List.of("fish", "salmon", "tuna", "cod", "tilapia", "anchovy", "sardine", "trout",
            "haddock", "halibut");
    static final List<String> SOY = List.of("soy", "soya", "tofu", "tempeh", "edamame", "miso");
    static final List<String> SESAME = List.of("sesame", "tahini");

    /** Meat terms excluded by vegetarian, vegan and pescatarian diets. */
    static final List<String> MEAT = List.of("chicken", "beef", "pork", "lamb", "turkey", "duck", "bacon", "ham",
            "sausage", "steak", "veal", "gelatin", "lard");

    /** Phrases that do not count as dairy (plant milks and non-dairy "butters"). */
    static final List<String> DAIRY_EXCEPTIONS = List.of("peanut butter", "almond butter", "cashew butter",
            "cocoa butter", "shea butter", "coconut milk", "coconut cream", "almond milk", "soy milk", "oat milk",
            "rice milk", "cashew milk");

    /** Gluten-free flours that do not count as gluten. */
    static final List<String> GLUTEN_EXCEPTIONS = List.of("rice flour", "almond flour", "coconut flour",
            "corn flour", "chickpea flour", "buckwheat flour");

    /** Allergen names a user may enter that stand for a whole group of terms. */
    private static final Map<String, List<String>> GROUPS = Map.ofEntries(
            Map.entry("peanut", PEANUT),
            Map.entry("nut", TREE_NUTS), Map.entry("nuts", TREE_NUTS),
            Map.entry("tree nut", TREE_NUTS), Map.entry("tree nuts", TREE_NUTS),
            Map.entry("dairy", DAIRY), Map.entry("milk", DAIRY),
            Map.entry("gluten", GLUTEN), Map.entry("wheat", GLUTEN),
            Map.entry("egg", EGG), Map.entry("eggs", EGG),
            Map.entry("shellfish", SHELLFISH),
            Map.entry("fish", FISH),
            Map.entry("soy", SOY), Map.entry("soya", SOY),
            Map.entry("sesame", SESAME));

    private FoodKeywords() {
    }

    /** The words of a text: normalized, split on every non-letter character. */
    static List<String> words(String text) {
        return Arrays.stream(Names.normalize(text).split("[^\\p{L}]+")).filter(word -> !word.isEmpty()).toList();
    }

    /**
     * Returns the terms an allergen stands for: always the literal (normalized) term, plus the group's
     * terms when it is a known group name such as "nuts" or "dairy".
     */
    static Set<String> expand(String allergen) {
        String literal = Names.normalize(allergen);
        Set<String> terms = new LinkedHashSet<>();
        terms.add(literal);
        terms.addAll(GROUPS.getOrDefault(literal, List.of()));
        return terms;
    }

    /** Returns the first of {@code allergen}'s terms found in {@code ingredientName}, honouring the exceptions. */
    static Optional<String> findAllergen(String allergen, String ingredientName) {
        List<String> group = GROUPS.get(Names.normalize(allergen));
        List<String> words = words(withoutExceptions(ingredientName, group));
        return expand(allergen).stream().filter(term -> containsTerm(words, term)).findFirst();
    }

    /** Returns the first term in {@code ingredientName} that {@code diet} excludes. */
    static Optional<String> findDietConflict(DietType diet, String ingredientName) {
        List<String> words = words(ingredientName);
        List<String> excluded = new ArrayList<>();
        switch (diet) {
            case OMNIVORE -> {
                return Optional.empty();
            }
            case PESCATARIAN -> excluded.addAll(MEAT);
            case VEGETARIAN, VEGAN -> {
                excluded.addAll(MEAT);
                excluded.addAll(FISH);
                excluded.addAll(SHELLFISH);
            }
        }
        if (diet == DietType.VEGAN) {
            excluded.addAll(EGG);
            excluded.add("honey");
        }
        Optional<String> found = excluded.stream().filter(term -> containsTerm(words, term)).findFirst();
        if (found.isEmpty() && diet == DietType.VEGAN) {
            List<String> dairyWords = words(withoutExceptions(ingredientName, DAIRY));
            found = DAIRY.stream().filter(term -> containsTerm(dairyWords, term)).findFirst();
        }
        return found;
    }

    /** Removes the dairy or gluten exception phrases from the name when checking that group. */
    private static String withoutExceptions(String ingredientName, List<String> group) {
        List<String> exceptions = group == DAIRY ? DAIRY_EXCEPTIONS : group == GLUTEN ? GLUTEN_EXCEPTIONS : List.of();
        String text = " " + String.join(" ", words(ingredientName)) + " ";
        for (String phrase : exceptions) {
            text = text.replace(" " + phrase + " ", " ");
        }
        return text;
    }

    /** Whether {@code term}'s words appear consecutively in {@code words}. */
    static boolean containsTerm(List<String> words, String term) {
        List<String> termWords = words(term);
        for (int start = 0; start + termWords.size() <= words.size(); start++) {
            boolean all = true;
            for (int i = 0; i < termWords.size() && all; i++) {
                all = sameWord(words.get(start + i), termWords.get(i));
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** Equal, or one is the other plus "s" or "es". */
    private static boolean sameWord(String a, String b) {
        return a.equals(b) || a.equals(b + "s") || a.equals(b + "es") || b.equals(a + "s") || b.equals(a + "es");
    }
}
