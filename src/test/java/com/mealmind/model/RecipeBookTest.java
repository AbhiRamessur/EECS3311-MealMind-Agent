package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeBookTest {

    private RecipeBook book;
    private List<ChangeEvent> events;

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        events = new ArrayList<>();
        book.addObserver(events::add);
    }

    private static Recipe recipe(String id, int servings) {
        Ingredient rice = new Ingredient("Rice", 100, Unit.GRAM, 200, 4, 45, 1, 0.50);
        return new Recipe(id, "Recipe " + id, servings, 10, List.of(), List.of(rice));
    }

    @Test
    void putNewReturnsEmptyAndPutAgainReturnsPrevious() {
        Recipe first = recipe("r1", 2);
        Recipe second = recipe("r1", 4);

        assertEquals(Optional.empty(), book.put(first));
        Optional<Recipe> previous = book.put(second);

        assertSame(first, previous.orElseThrow());
        assertSame(second, book.get("r1").orElseThrow());
        assertEquals(1, book.size());
        assertTrue(book.contains("r1"));
        assertEquals(2, events.size());
    }

    @Test
    void removeReturnsRecipeThenEmptyAndNotifiesOnlyOnce() {
        Recipe recipe = recipe("r1", 2);
        book.put(recipe);
        events.clear();

        assertSame(recipe, book.remove("r1").orElseThrow());
        assertEquals(Optional.empty(), book.remove("r1"));
        assertEquals(Optional.empty(), book.remove("missing"));

        assertFalse(book.contains("r1"));
        assertEquals(1, events.size());
    }

    @Test
    void getAllIsSortedByIdAndUnmodifiable() {
        book.put(recipe("r3", 1));
        book.put(recipe("r1", 1));
        book.put(recipe("r2", 1));

        List<Recipe> all = book.getAll();

        assertEquals(List.of("r1", "r2", "r3"), all.stream().map(Recipe::getId).toList());
        assertThrows(UnsupportedOperationException.class, () -> all.add(recipe("r4", 1)));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> book.put(null));
        assertThrows(NullPointerException.class, () -> book.remove(null));
        assertThrows(NullPointerException.class, () -> book.get(null));
        assertThrows(NullPointerException.class, () -> book.contains(null));
    }
}
