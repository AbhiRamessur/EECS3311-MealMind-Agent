package com.mealmind.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fixture (all 1 serving): K1 Lentil Soup (Lentils, Onion; 30 min), K2 Beef Stew (Beef, Onion; 60 min),
 * K3 Oat Bowl (Oats, Honey; 5 min), K4 Rice Salad (Rice, Cucumber, Tomato, Feta, Olives; 15 min).
 * With the pantry holding lentils and onion: K1 100%, K2 50%, K3 0% (2 missing), K4 0% (5 missing).
 */
class RecipeRecommenderTest {

    private Pantry pantry;
    private UserProfile profile;
    private RecipeBook book;
    private RecipePreferences preferences;
    private RecipeRecommender recommender;

    private static Ingredient grams(String name) {
        return new Ingredient(name, 100, Unit.GRAM, 100, 5, 10, 2, 1.00);
    }

    private static Recipe recipe(String id, String name, int prep, String... ingredients) {
        return new Recipe(id, name, 1, prep, List.of("Cook"), java.util.Arrays.stream(ingredients).map(RecipeRecommenderTest::grams).toList());
    }

    @BeforeEach
    void setUp() {
        pantry = new Pantry();
        pantry.addItem(new PantryItem("Lentils", 500, Unit.GRAM));
        pantry.addItem(new PantryItem("Onion", 500, Unit.GRAM));
        profile = new UserProfile();
        use(recipe("K1", "Lentil Soup", 30, "Lentils", "Onion"), recipe("K2", "Beef Stew", 60, "Beef", "Onion"),
                recipe("K3", "Oat Bowl", 5, "Oats", "Honey"),
                recipe("K4", "Rice Salad", 15, "Rice", "Cucumber", "Tomato", "Feta", "Olives"));
    }

    private void use(Recipe... recipes) {
        book = new RecipeBook();
        for (Recipe recipe : recipes) {
            book.put(recipe);
        }
        preferences = new RecipePreferences(book);
        recommender = new RecipeRecommender(book, pantry, new ConstraintValidator(profile), preferences);
    }

    private RecommendationResult recommend(boolean pantryFirst, Integer maxPrep, int limit) {
        return recommender.recommend(new RecommendationRequest(pantryFirst, maxPrep, limit));
    }

    private static List<String> ids(RecommendationResult result) {
        return result.items().stream().map(item -> item.recipe().getId()).toList();
    }

    @Test
    void unsafeRecipesNeverAppear() {
        profile.setDiet(DietType.VEGETARIAN);

        assertEquals(List.of("K1", "K3", "K4"), ids(recommend(true, null, 10)));
    }

    @Test
    void noSafeRecipeThrows() {
        use(recipe("K2", "Beef Stew", 60, "Beef", "Onion"));
        profile.setDiet(DietType.VEGETARIAN);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> recommend(false, null, 5));
        assertEquals("No recipes in the recipe book are safe for this profile", e.getMessage());
    }

    @Test
    void lowRatedRecipesAreDroppedUnlessNothingWouldBeLeft() {
        preferences.rate("K2", 1);
        RecommendationResult result = recommend(true, null, 10);
        assertEquals(List.of("K1", "K3", "K4"), ids(result));
        assertEquals(List.of("Excluded 1 recipe(s) you rated 1-2 stars."), result.notes());

        for (String id : List.of("K1", "K3", "K4")) {
            preferences.rate(id, 2);
        }
        RecommendationResult allLow = recommend(true, null, 10);
        assertEquals(4, allLow.items().size());
        assertTrue(allLow.notes().isEmpty());
    }

    @Test
    void maxTimeFiltersAndIsRelaxedWhenNothingFits() {
        assertEquals(List.of("K3", "K4"), ids(recommend(false, 20, 10)));

        RecommendationResult relaxed = recommend(true, 1, 10);
        assertEquals(List.of("No recipe fits 1 minutes; showing the closest matches instead."), relaxed.notes());
        assertEquals(List.of("K3", "K4", "K1", "K2"), ids(relaxed));
    }

    @Test
    void pantryFirstAndPreferenceFirstDisagree() {
        preferences.setFavorite("K4", true);
        preferences.rate("K3", 5);

        // Bonuses: K4 +5, K3 +2. Preference first: bonus x 100 + coverage.
        assertEquals(List.of("K4", "K3", "K1", "K2"), ids(recommend(false, null, 10)));
        // Pantry first: coverage x 100 + bonus.
        assertEquals(List.of("K1", "K2", "K4", "K3"), ids(recommend(true, null, 10)));
    }

    @Test
    void favoriteAndRatingBonusesAndTieBreakers() {
        assertEquals(List.of("K1", "K2", "K3", "K4"), ids(recommend(true, null, 10)), "K3 has fewer missing than K4");
        preferences.rate("K4", 4);
        assertEquals(List.of("K1", "K2", "K4", "K3"), ids(recommend(true, null, 10)));
        preferences.setFavorite("K3", true);
        assertEquals(List.of("K1", "K2", "K3", "K4"), ids(recommend(true, null, 10)));

        use(recipe("T2", "Twin B", 10, "Oats"), recipe("T1", "Twin A", 10, "Oats"));
        assertEquals(List.of("T1", "T2"), ids(recommend(true, null, 10)));
    }

    @Test
    void emptyPantryRanksByPreferenceThenId() {
        pantry.removeItem("Lentils");
        pantry.removeItem("Onion");
        preferences.setFavorite("K4", true);

        RecommendationResult result = recommend(true, null, 10);

        assertEquals(List.of("Pantry is empty; ranking uses your ratings and favorites only."), result.notes());
        assertEquals(List.of("K4", "K1", "K2", "K3"), ids(result));
    }

    @Test
    void limitKeepsTheBest() {
        assertEquals(List.of("K1", "K2"), ids(recommend(true, null, 2)));
    }

    @Test
    void reasonsAreDeterministicSentences() {
        preferences.setFavorite("K1", true);
        preferences.rate("K1", 4);

        List<Recommendation> items = recommend(true, null, 10).items();

        assertEquals("Pantry covers 100% of the recipe; favorite; rated 4/5; 30 min", items.get(0).reason());
        assertEquals("Pantry covers 50% of the recipe; missing: Beef; 60 min", items.get(1).reason());
        assertEquals("Pantry covers 0% of the recipe; missing: Rice, Cucumber, Tomato and 2 more; 15 min", items.get(3).reason());
        assertEquals(List.of("Rice", "Cucumber", "Tomato", "Feta", "Olives"), items.get(3).missing());
        assertTrue(items.get(0).favorite());
        assertEquals(4, items.get(0).rating());
        assertEquals(100, items.get(0).coveragePercent());
    }

    @Test
    void isDeterministicAndChangesNothing() {
        RecommendationResult first = recommend(false, 20, 5);

        assertEquals(first, recommend(false, 20, 5));
        assertEquals(4, book.size());
        assertEquals(500, pantry.availableQuantity("Lentils", Unit.GRAM), 1e-9);
        assertFalse(preferences.isFavorite("K1"));
    }

    @Test
    void recordsValidateAndCopy() {
        Recommendation item = recommend(true, null, 1).items().get(0);

        assertThrows(IllegalArgumentException.class, () -> item.withReason(" "));
        assertEquals("Other", item.withReason("Other").reason());
        assertThrows(UnsupportedOperationException.class, () -> item.missing().add("x"));
        assertThrows(NullPointerException.class, () -> recommender.recommend(null));
    }
}
