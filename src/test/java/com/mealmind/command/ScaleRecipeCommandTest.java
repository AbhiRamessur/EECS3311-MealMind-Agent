package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ScaleRecipeCommandTest {

    private static Recipe lasagna() {
        Ingredient pasta = new Ingredient("Lasagna sheets", 250, Unit.GRAM, 900, 30, 180, 4, 2.00);
        Ingredient beef = new Ingredient("Ground beef", 500, Unit.GRAM, 1250, 130, 0, 80, 7.00);
        return new Recipe("lasagna", "Lasagna", 4, 60, List.of("Layer", "Bake"), List.of(pasta, beef));
    }

    @Test
    void scalesThenUndoRestoresOriginalInstanceThenRedoReapplies() {
        RecipeBook book = new RecipeBook();
        Recipe original = lasagna();
        book.put(original);
        ScaleRecipeCommand command = new ScaleRecipeCommand(book, "lasagna", 6);

        command.execute();
        Recipe scaled = book.get("lasagna").orElseThrow();
        assertEquals(6, scaled.getServings());
        assertEquals(375, scaled.getIngredients().get(0).getQuantity(), 1e-9);

        command.undo();
        assertSame(original, book.get("lasagna").orElseThrow());
        assertEquals(original, book.get("lasagna").orElseThrow());

        command.execute();
        assertEquals(6, book.get("lasagna").orElseThrow().getServings());
        assertEquals("Scale Lasagna to 6 servings", command.description());
    }

    @Test
    void missingRecipeThrowsAndLeavesBookUnchanged() {
        RecipeBook book = new RecipeBook();
        Recipe original = lasagna();
        book.put(original);
        ScaleRecipeCommand command = new ScaleRecipeCommand(book, "missing", 6);

        assertThrows(NoSuchElementException.class, command::execute);

        assertEquals(List.of(original), book.getAll());
        assertThrows(IllegalStateException.class, command::undo);
    }

    @Test
    void invalidArgumentsAreRejectedAtConstruction() {
        RecipeBook book = new RecipeBook();

        assertThrows(IllegalArgumentException.class, () -> new ScaleRecipeCommand(book, "lasagna", 0));
        assertThrows(NullPointerException.class, () -> new ScaleRecipeCommand(null, "lasagna", 2));
        assertThrows(NullPointerException.class, () -> new ScaleRecipeCommand(book, null, 2));
    }
}
