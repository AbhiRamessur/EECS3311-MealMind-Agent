package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.command.AddPantryItemCommand;
import com.mealmind.command.CommandHistory;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import org.junit.jupiter.api.Test;

class UndoRedoModelTest {

    private final Pantry pantry = new Pantry();
    private final CommandHistory history = new CommandHistory();
    private final UndoRedoModel model = new UndoRedoModel(history);

    @Test
    void emptyHistoryLabelsAndMessages() {
        assertEquals("Undo", model.undoLabel());
        assertEquals("Redo", model.redoLabel());
        assertFalse(model.canUndo());
        assertFalse(model.canRedo());
        assertEquals("Nothing to undo.", model.undo());
        assertEquals("Nothing to redo.", model.redo());
    }

    @Test
    void followsARealHistory() {
        history.pushAndExecute(new AddPantryItemCommand(pantry, new PantryItem("Rice", 500, Unit.GRAM)));
        history.pushAndExecute(new AddPantryItemCommand(pantry, new PantryItem("Beans", 200, Unit.GRAM)));
        assertEquals("Undo: Add Beans to pantry", model.undoLabel());
        assertTrue(model.canUndo());

        assertEquals("Undone: Add Beans to pantry", model.undo());
        assertEquals("Undo: Add Rice to pantry", model.undoLabel());
        assertEquals("Redo: Add Beans to pantry", model.redoLabel());
        assertTrue(pantry.getItem("Beans").isEmpty());

        assertEquals("Redone: Add Beans to pantry", model.redo());
        assertTrue(pantry.getItem("Beans").isPresent());
        assertFalse(model.canRedo());

        model.undo();
        model.undo();
        assertFalse(model.canUndo());
        assertTrue(model.canRedo());
        assertEquals("Nothing to undo.", model.undo());
        assertTrue(pantry.isEmpty());
    }
}
