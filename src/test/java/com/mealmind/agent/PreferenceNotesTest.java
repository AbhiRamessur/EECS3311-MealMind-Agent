package com.mealmind.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.preferences.PreferenceData;
import com.mealmind.preferences.RecipePreferences;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreferenceNotesTest {

    private RecipeBook book;
    private RecipePreferences prefs;

    private void add(String id, String name) {
        Ingredient rice = new Ingredient("Rice", 100, Unit.GRAM, 130, 3, 28, 0, 0.20);
        book.put(new Recipe(id, name, 1, 10, List.of("Cook"), List.of(rice)));
    }

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        for (int i = 1; i <= 8; i++) {
            add("R" + i, "Dish " + i);
        }
        prefs = new RecipePreferences(book);
    }

    @Test
    void noLinesWithoutPreferences() {
        assertEquals(List.of(), PreferenceNotes.build(prefs, book));
    }

    @Test
    void threeLinesInTheirOrder() {
        prefs.rate("R1", 4);
        prefs.rate("R2", 5);
        prefs.rate("R3", 3);
        prefs.rate("R4", 1);
        prefs.rate("R5", 2);
        prefs.setFavorite("R6", true);
        prefs.setFavorite("R2", true);

        assertEquals(List.of(
                "Recipes the user rated highly (4-5 stars): Dish 2; Dish 1.",
                "Recipes the user rated poorly (1-2 stars; avoid similar dishes): Dish 4; Dish 5.",
                "Recipes the user marked as favorites: Dish 2; Dish 6."), PreferenceNotes.build(prefs, book));
    }

    @Test
    void atMostFiveNamesPerLine() {
        for (int i = 1; i <= 7; i++) {
            prefs.rate("R" + i, 5);
        }

        assertEquals(List.of("Recipes the user rated highly (4-5 stars): Dish 1; Dish 2; Dish 3; Dish 4; Dish 5."),
                PreferenceNotes.build(prefs, book));
    }

    @Test
    void idsMissingFromTheBookAreSkipped() {
        prefs.rate("R1", 5);
        prefs.rate("R2", 5);
        RecipeBook other = new RecipeBook();
        other.put(book.get("R2").orElseThrow());

        assertEquals(List.of("Recipes the user rated highly (4-5 stars): Dish 2."), PreferenceNotes.build(prefs, other));
    }

    @Test
    void namesAreSanitized() {
        add("X1", "Evil \"quote\" {braces} back\\slash\tand\nnewline");
        add("X2", "A".repeat(70));
        prefs.applyData(new PreferenceData(Map.of("X1", 5, "X2", 4), Set.of()));

        String line = PreferenceNotes.build(prefs, book).get(0);

        assertEquals("Recipes the user rated highly (4-5 stars): Evil quote braces back slash and newline; "
                + "A".repeat(59) + "….", line);
    }

    @Test
    void isDeterministic() {
        prefs.rate("R3", 5);
        prefs.setFavorite("R1", true);

        assertEquals(PreferenceNotes.build(prefs, book), PreferenceNotes.build(prefs, book));
    }
}
