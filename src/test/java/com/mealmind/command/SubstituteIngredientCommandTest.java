package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubstituteIngredientCommandTest {

    private static final Ingredient NOODLES = new Ingredient("Noodles", 200, Unit.GRAM, 700, 24, 140, 4, 1.00);
    private static final Ingredient PEANUT_BUTTER = new Ingredient("Peanut butter", 100, Unit.GRAM, 600, 24, 20, 50, 2.00);
    private static final Ingredient SUNFLOWER = new Ingredient("Sunflower seed butter", 100, Unit.GRAM, 580, 20, 20, 48, 3.00);

    private RecipeBook book;
    private Recipe original;

    @BeforeEach
    void setUp() {
        original = new Recipe("T1", "Peanut Noodles", 2, 15, List.of("Cook the noodles.", "Stir in the peanut butter."),
                List.of(NOODLES, PEANUT_BUTTER));
        book = new RecipeBook();
        book.put(original);
    }

    @Test
    void executeReplacesExactlyOneIngredientAndKeepsEverythingElse() {
        SubstituteIngredientCommand command = new SubstituteIngredientCommand(book, "T1", 1, SUNFLOWER);

        command.execute();

        Recipe updated = book.get("T1").orElseThrow();
        assertEquals(List.of(NOODLES, SUNFLOWER), updated.getIngredients());
        assertEquals("T1", updated.getId());
        assertEquals("Peanut Noodles", updated.getName());
        assertEquals(2, updated.getServings());
        assertEquals(15, updated.getPrepMinutes());
        assertEquals(original.getInstructions(), updated.getInstructions());
    }

    @Test
    void undoRestoresTheSameInstanceAndRedoReapplies() {
        SubstituteIngredientCommand command = new SubstituteIngredientCommand(book, "T1", 1, SUNFLOWER);
        command.execute();

        command.undo();
        assertSame(original, book.get("T1").orElseThrow());

        command.execute();
        assertEquals(SUNFLOWER, book.get("T1").orElseThrow().getIngredients().get(1));
    }

    @Test
    void missingRecipeOrBadIndexLeavesTheBookUnchanged() {
        SubstituteIngredientCommand missing = new SubstituteIngredientCommand(book, "T9", 0, SUNFLOWER);
        assertEquals("No recipe with id 'T9'", assertThrows(NoSuchElementException.class, missing::execute).getMessage());
        assertThrows(IllegalStateException.class, missing::undo);

        for (int index : new int[] {-1, 2}) {
            SubstituteIngredientCommand bad = new SubstituteIngredientCommand(book, "T1", index, SUNFLOWER);
            assertThrows(IllegalArgumentException.class, bad::execute);
            assertThrows(IllegalStateException.class, bad::undo);
        }
        assertSame(original, book.get("T1").orElseThrow());
    }

    @Test
    void descriptionUsesIdsBeforeAndNamesAfterExecution() {
        SubstituteIngredientCommand command = new SubstituteIngredientCommand(book, "T1", 1, SUNFLOWER);
        assertEquals("Substitute ingredient 2 of T1 with Sunflower seed butter", command.description());

        command.execute();

        assertEquals("Substitute Peanut butter with Sunflower seed butter in Peanut Noodles", command.description());
    }

    @Test
    void commandContractAndNullChecks() {
        SubstituteIngredientCommand command = new SubstituteIngredientCommand(book, "T1", 1, SUNFLOWER);
        assertThrows(IllegalStateException.class, command::undo);
        command.execute();
        assertThrows(IllegalStateException.class, command::execute);

        assertThrows(NullPointerException.class, () -> new SubstituteIngredientCommand(null, "T1", 0, SUNFLOWER));
        assertThrows(NullPointerException.class, () -> new SubstituteIngredientCommand(book, null, 0, SUNFLOWER));
        assertThrows(NullPointerException.class, () -> new SubstituteIngredientCommand(book, "T1", 0, null));
    }

    @Test
    void worksThroughTheHistory() {
        CommandHistory history = new CommandHistory();

        history.pushAndExecute(new SubstituteIngredientCommand(book, "T1", 1, SUNFLOWER));
        assertEquals("Substitute Peanut butter with Sunflower seed butter in Peanut Noodles",
                history.nextUndoDescription().orElseThrow());

        assertTrue(history.undo());
        assertSame(original, book.get("T1").orElseThrow());
        assertTrue(history.redo());
        assertEquals(SUNFLOWER, book.get("T1").orElseThrow().getIngredients().get(1));
    }
}
