package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Saving a recipe to the book through an undoable command. */
class SaveRecipeCommandTest {

    private RecipeBook book;

    private static Recipe recipe(String id, String name) {
        Ingredient rice = new Ingredient("Rice", 100, Unit.GRAM, 130, 3, 28, 0, 0.20);
        return new Recipe(id, name, 2, 10, List.of("Cook"), List.of(rice));
    }

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        book.put(recipe("R1", "Plain Rice"));
    }

    @Test
    void saveExecuteUndoRedo() {
        Recipe bowl = recipe("AI001", "Rice Bowl");
        SaveRecipeCommand command = new SaveRecipeCommand(book, bowl);

        command.execute();
        assertSame(bowl, book.get("AI001").orElseThrow());
        command.undo();
        assertFalse(book.contains("AI001"));
        command.execute();
        assertTrue(book.contains("AI001"));
        assertEquals("Save recipe Rice Bowl (AI001)", command.description());
    }

    @Test
    void saveRejectsDuplicateIdAndMisuse() {
        SaveRecipeCommand duplicate = new SaveRecipeCommand(book, recipe("R1", "Other"));
        assertEquals("A recipe with id R1 already exists", assertThrows(IllegalStateException.class, duplicate::execute).getMessage());
        assertEquals("Plain Rice", book.get("R1").orElseThrow().getName());
        assertThrows(IllegalStateException.class, duplicate::undo);

        SaveRecipeCommand command = new SaveRecipeCommand(book, recipe("AI001", "Bowl"));
        assertThrows(IllegalStateException.class, command::undo);
        command.execute();
        assertThrows(IllegalStateException.class, command::execute);
        assertThrows(NullPointerException.class, () -> new SaveRecipeCommand(null, recipe("X1", "X")));
        assertThrows(NullPointerException.class, () -> new SaveRecipeCommand(book, null));
    }
}
