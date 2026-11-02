package com.mealmind.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.ChangeEvent;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipePreferencesTest {

    private RecipeBook book;
    private RecipePreferences prefs;
    private List<ChangeEvent> events;

    private static Recipe recipe(String id) {
        Ingredient rice = new Ingredient("Rice", 100, Unit.GRAM, 130, 3, 28, 0, 0.20);
        return new Recipe(id, "Recipe " + id, 1, 10, List.of("Cook"), List.of(rice));
    }

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        for (String id : List.of("R1", "R2", "R3", "R4")) {
            book.put(recipe(id));
        }
        prefs = new RecipePreferences(book);
        events = new ArrayList<>();
        prefs.addObserver(events::add);
    }

    @Test
    void rateAndReRateNotifyOnlyOnChange() {
        assertTrue(prefs.rate(" R1 ", 4));
        assertFalse(prefs.rate("R1", 4));
        assertTrue(prefs.rate("R1", 5));

        assertEquals(Optional.of(5), prefs.getRating("R1"));
        assertEquals(List.of("Rated R1: 4/5", "Rated R1: 5/5"), events.stream().map(ChangeEvent::description).toList());
    }

    @Test
    void starsMustBeOneToFive() {
        assertEquals("Rating must be between 1 and 5", assertThrows(IllegalArgumentException.class, () -> prefs.rate("R1", 0)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> prefs.rate("R1", 6));
        assertTrue(prefs.rate("R1", 1));
        assertTrue(prefs.rate("R2", 5));
        assertEquals(2, events.size());
    }

    @Test
    void unknownRecipeIsRejected() {
        assertEquals("No recipe with id 'R9'", assertThrows(NoSuchElementException.class, () -> prefs.rate("R9", 3)).getMessage());
        assertThrows(NoSuchElementException.class, () -> prefs.setFavorite("R9", true));
        assertThrows(NoSuchElementException.class, () -> prefs.setFavorite("R9", false));
        assertThrows(NullPointerException.class, () -> prefs.rate(null, 3));
        assertEquals(0, events.size());
    }

    @Test
    void clearRating() {
        prefs.rate("R1", 3);
        events.clear();

        assertTrue(prefs.clearRating("R1"));
        assertFalse(prefs.clearRating("R1"));

        assertEquals(Optional.empty(), prefs.getRating("R1"));
        assertEquals(1, events.size());
    }

    @Test
    void favoritesAreIdempotent() {
        assertTrue(prefs.setFavorite("R2", true));
        assertFalse(prefs.setFavorite("R2", true));
        assertTrue(prefs.isFavorite("R2"));
        assertTrue(prefs.setFavorite("R2", false));
        assertFalse(prefs.setFavorite("R2", false));

        assertEquals(List.of("Favorited R2", "Removed R2 from favorites"), events.stream().map(ChangeEvent::description).toList());
    }

    @Test
    void viewsAreSortedAndUnmodifiable() {
        prefs.rate("R3", 2);
        prefs.rate("R1", 5);
        prefs.setFavorite("R4", true);
        prefs.setFavorite("R2", true);

        assertEquals(List.of("R1", "R3"), List.copyOf(prefs.ratings().keySet()));
        assertEquals(List.of("R2", "R4"), List.copyOf(prefs.favorites()));
        assertThrows(UnsupportedOperationException.class, () -> prefs.ratings().put("R2", 1));
        assertThrows(UnsupportedOperationException.class, () -> prefs.favorites().add("R1"));
    }

    @Test
    void topRatedOrderingLimitsAndArguments() {
        prefs.rate("R1", 4);
        prefs.rate("R2", 5);
        prefs.rate("R3", 4);
        prefs.rate("R4", 2);

        assertEquals(List.of("R2", "R1", "R3"), prefs.topRated(10, 4));
        assertEquals(List.of("R2", "R1"), prefs.topRated(2, 1));
        assertThrows(IllegalArgumentException.class, () -> prefs.topRated(0, 4));
        assertThrows(IllegalArgumentException.class, () -> prefs.topRated(3, 0));
        assertThrows(IllegalArgumentException.class, () -> prefs.topRated(3, 6));
    }

    @Test
    void applyDataValidatesFirstDropsUnknownAndNotifiesOnce() {
        prefs.rate("R1", 3);
        events.clear();

        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> prefs.applyData(new PreferenceData(Map.of("R2", 4, "R3", 9), Set.of())));
        assertTrue(invalid.getMessage().contains("R3"), invalid.getMessage());
        assertEquals(Map.of("R1", 3), prefs.ratings());
        assertEquals(0, events.size());

        List<String> dropped = prefs.applyData(new PreferenceData(Map.of("R2", 4, "GONE", 5), Set.of("R3", "OLD")));

        assertEquals(List.of("GONE", "OLD"), dropped);
        assertEquals(Map.of("R2", 4), prefs.ratings());
        assertEquals(Set.of("R3"), prefs.favorites());
        assertEquals(1, events.size());
        prefs.applyData(prefs.toData());
        assertEquals(1, events.size());
    }

    @Test
    void removingARecipePrunesItsPreferences() {
        prefs.rate("R1", 5);
        prefs.setFavorite("R1", true);
        prefs.rate("R2", 3);
        events.clear();

        book.put(recipe("R5"));
        assertEquals(0, events.size());
        book.remove("R4");
        assertEquals(0, events.size());

        book.remove("R1");

        assertEquals(Map.of("R2", 3), prefs.ratings());
        assertEquals(Set.of(), prefs.favorites());
        assertEquals(1, events.size());
    }

    @Test
    void toDataApplyDataRoundTrip() {
        prefs.rate("R1", 5);
        prefs.setFavorite("R3", true);

        RecipePreferences copy = new RecipePreferences(book);
        assertEquals(List.of(), copy.applyData(prefs.toData()));

        assertEquals(prefs.toData(), copy.toData());
    }
}
