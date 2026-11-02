package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Removing a recipe from the book through an undoable command. */
class RemoveRecipeCommandTest {

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
    void removeExecuteUndoRedo() {
        Recipe original = book.get("R1").orElseThrow();
        RemoveRecipeCommand command = new RemoveRecipeCommand(book, " R1 ");

        command.execute();
        assertFalse(book.contains("R1"));
        command.undo();
        assertSame(original, book.get("R1").orElseThrow());
        command.execute();
        assertFalse(book.contains("R1"));
        assertEquals("Remove recipe R1", command.description());
    }

    @Test
    void removeMissingIdChangesNothingAndMisuseIsRejected() {
        RemoveRecipeCommand missing = new RemoveRecipeCommand(book, "R9");

        assertEquals("No recipe with id 'R9'", assertThrows(NoSuchElementException.class, missing::execute).getMessage());
        assertEquals(1, book.size());
        assertThrows(IllegalStateException.class, missing::undo);

        RemoveRecipeCommand command = new RemoveRecipeCommand(book, "R1");
        command.execute();
        assertThrows(IllegalStateException.class, command::execute);
        assertThrows(NullPointerException.class, () -> new RemoveRecipeCommand(book, null));
    }
}
