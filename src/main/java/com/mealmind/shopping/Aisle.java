package com.mealmind.shopping;

import com.mealmind.model.Names;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Store sections used to group a {@link ShoppingList}. Declaration order is display order.
 *
 * <p>{@link #classify(String)} is a <strong>documented heuristic, not a food database</strong>:
 * Stage 1 says shopping items are "grouped by aisle" but defines no category source, so each aisle
 * has a short list of common grocery keywords. Matching is by whole words or phrases, the longest
 * matching keyword wins (so "peanut butter" beats "butter"), ties go to the earlier aisle, and
 * anything unrecognized is {@link #OTHER}. Known limitations: unlisted foods fall into OTHER, and a
 * specific keyword can outrank a more general one (e.g. "frozen spinach" is listed explicitly
 * because "spinach" is longer than "frozen").
 */
public enum Aisle {
    PRODUCE("Produce"),
    MEAT_SEAFOOD("Meat & Seafood"),
    DAIRY_EGGS("Dairy & Eggs"),
    BAKERY("Bakery"),
    PANTRY_STAPLES("Pantry Staples"),
    FROZEN("Frozen"),
    OTHER("Other");

    private static final Map<Aisle, List<String>> KEYWORDS = new EnumMap<>(Aisle.class);

    static {
        KEYWORDS.put(PRODUCE, List.of(
                "tomato", "tomatoes", "onion", "onions", "garlic", "spinach", "apple", "apples",
                "banana", "bananas", "lemon", "lemons", "lime", "limes", "carrot", "carrots", "potato",
                "potatoes", "broccoli", "lettuce", "pepper", "peppers", "bell pepper", "mushroom",
                "mushrooms", "avocado", "avocados", "cucumber", "zucchini", "celery", "ginger",
                "cilantro", "parsley", "kale", "berries", "strawberries", "blueberries", "orange",
                "oranges", "peas", "corn", "cabbage", "cauliflower", "sweet potato"));
        KEYWORDS.put(MEAT_SEAFOOD, List.of(
                "chicken", "beef", "ground beef", "pork", "turkey", "salmon", "shrimp", "tuna",
                "bacon", "lamb", "sausage", "ham", "cod", "tilapia", "fish", "steak"));
        KEYWORDS.put(DAIRY_EGGS, List.of(
                "milk", "cheese", "yogurt", "butter", "cream", "sour cream", "egg", "eggs",
                "mozzarella", "cheddar", "parmesan", "feta"));
        KEYWORDS.put(BAKERY, List.of(
                "bread", "bun", "buns", "bagel", "bagels", "tortilla", "tortillas", "pita",
                "baguette", "croissant", "naan"));
        KEYWORDS.put(PANTRY_STAPLES, List.of(
                "rice", "pasta", "spaghetti", "noodles", "flour", "sugar", "salt", "oil", "olive oil",
                "beans", "black beans", "lentils", "oats", "quinoa", "cereal", "honey", "black pepper",
                "cumin", "paprika", "cinnamon", "oregano", "chili powder", "turmeric", "canned",
                "peanut butter", "coconut milk", "soy sauce", "vinegar", "tomato paste",
                "tomato sauce", "broth", "stock", "chicken broth", "chicken stock", "baking powder",
                "baking soda", "yeast", "chickpeas"));
        KEYWORDS.put(FROZEN, List.of(
                "frozen", "frozen peas", "frozen spinach", "frozen corn", "frozen berries", "ice cream"));
        KEYWORDS.put(OTHER, List.of());
    }

    private final String displayName;

    Aisle(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the heading shown for this aisle, e.g. {@code "Meat & Seafood"}.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Guesses the aisle for an ingredient name using the keyword heuristic described above.
     *
     * @param name the ingredient name, in any case and spacing
     * @return the aisle of the longest matching keyword, or {@link #OTHER} if none matches
     * @throws NullPointerException if {@code name} is null
     */
    public static Aisle classify(String name) {
        // Punctuation becomes a space so "Salt, to taste" still contains the word "salt".
        Objects.requireNonNull(name, "name");
        String words = " " + Names.normalize(name.replaceAll("[^\\p{L}\\p{N}]+", " ")) + " ";
        Aisle best = OTHER;
        int bestLength = 0;
        for (Aisle aisle : values()) {
            for (String keyword : KEYWORDS.get(aisle)) {
                if (keyword.length() > bestLength && words.contains(" " + keyword + " ")) {
                    best = aisle;
                    bestLength = keyword.length();
                }
            }
        }
        return best;
    }
}
