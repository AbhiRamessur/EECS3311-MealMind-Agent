package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.persistence.RecipeRepository;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeRowsTest {

    private RecipeBook book;
    private RecipePreferences prefs;
    private UserProfile profile;
    private ConstraintValidator validator;
    private final Set<String> bundled = Set.of("R101", "R102", "R103", "R104", "R105", "R106", "R107", "R108", "R109", "R110");

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES).forEach(book::put);
        prefs = new RecipePreferences(book);
        profile = new UserProfile();
        validator = new ConstraintValidator(profile);
    }

    private List<RecipeRow> rows(boolean favoritesOnly, String filter) {
        return RecipeRows.of(book, prefs, validator, bundled::contains, favoritesOnly, filter);
    }

    private static List<String> ids(List<RecipeRow> rows) {
        return rows.stream().map(RecipeRow::id).toList();
    }

    @Test
    void sortedByIdWithRatingsFavoritesAndBundledFlag() {
        prefs.rate("R103", 4);
        prefs.setFavorite("R105", true);
        book.put(new Recipe("AI001", "Lime Rice", 2, 15, List.of("Cook"),
                List.of(new Ingredient("Rice", 200, Unit.GRAM, 701, 13, 155, 1, 1.0))));

        List<RecipeRow> rows = rows(false, null);

        assertEquals(List.of("AI001", "R101", "R102", "R103", "R104", "R105", "R106", "R107", "R108", "R109", "R110"), ids(rows));
        RecipeRow ai = rows.get(0);
        assertEquals(new RecipeRow("AI001", "Lime Rice", "2", "15 min", "351", "-", false, false, true, ""), ai);
        RecipeRow soup = rows.get(3);
        assertEquals("4/5", soup.ratingText());
        assertTrue(soup.bundled());
        assertTrue(rows.get(5).favorite());
    }

    @Test
    void filterMatchesIdNameOrIngredientIgnoringCaseAndBlankMeansNone() {
        assertEquals(List.of("R103"), ids(rows(false, "  LENTIL ")));
        assertEquals(List.of("R105"), ids(rows(false, "r105")));
        assertEquals(List.of("R101", "R103", "R104"), ids(rows(false, "garlic")));
        assertEquals(10, rows(false, "   ").size());
        assertEquals(List.of(), ids(rows(false, "no such thing")));
    }

    @Test
    void favoritesOnly() {
        prefs.setFavorite("R108", true);
        prefs.setFavorite("R102", true);

        assertEquals(List.of("R102", "R108"), ids(rows(true, "")));
        assertEquals(List.of("R108"), ids(rows(true, "salad")));
    }

    @Test
    void unsafeRecipesAreListedButFlagged() {
        profile.setDiet(DietType.VEGETARIAN);

        List<RecipeRow> rows = rows(false, "");

        assertEquals(10, rows.size());
        assertEquals(List.of("R101", "R104", "R106"), rows.stream().filter(r -> !r.safe()).map(RecipeRow::id).toList());
        RecipeRow chicken = rows.get(0);
        assertFalse(chicken.safe());
        assertEquals("Not safe for your profile", chicken.safetyText());
        assertEquals("", rows.get(2).safetyText());
    }

    @Test
    void perServingCaloriesAreWholeNumbers() {
        Recipe recipe = book.get("R103").orElseThrow();

        String expected = String.format(java.util.Locale.ROOT, "%.0f", recipe.caloriesPerServing());
        assertEquals(expected, rows(false, "R103").get(0).caloriesText());
        assertFalse(rows(false, "R103").get(0).caloriesText().contains("."));
    }
}
