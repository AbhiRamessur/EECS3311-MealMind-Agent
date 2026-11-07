package com.mealmind.gui.logic;

import com.mealmind.command.CommandHistory;
import java.util.Objects;
import java.util.Optional;

/**
 * The undo/redo toolbar's state and actions over the shared {@link CommandHistory}. Messages match the
 * CLI's {@code undo} and {@code redo} output.
 */
public final class UndoRedoModel {

    private final CommandHistory history;

    /**
     * Creates the model.
     *
     * @param history the session's command history
     * @throws NullPointerException if {@code history} is null
     */
    public UndoRedoModel(CommandHistory history) {
        this.history = Objects.requireNonNull(history, "history");
    }

    /**
     * Returns whether there is something to undo.
     *
     * @return {@code true} if undo is possible
     */
    public boolean canUndo() {
        return history.canUndo();
    }

    /**
     * Returns whether there is something to redo.
     *
     * @return {@code true} if redo is possible
     */
    public boolean canRedo() {
        return history.canRedo();
    }

    /**
     * Returns the undo button text.
     *
     * @return "Undo: &lt;description&gt;" or "Undo"
     */
    public String undoLabel() {
        return history.nextUndoDescription().map(d -> "Undo: " + d).orElse("Undo");
    }

    /**
     * Returns the redo button text.
     *
     * @return "Redo: &lt;description&gt;" or "Redo"
     */
    public String redoLabel() {
        return history.nextRedoDescription().map(d -> "Redo: " + d).orElse("Redo");
    }

    /**
     * Undoes the last command.
     *
     * @return "Undone: &lt;description&gt;" or "Nothing to undo."
     */
    public String undo() {
        Optional<String> description = history.nextUndoDescription();
        return history.undo() ? "Undone: " + description.orElseThrow() : "Nothing to undo.";
    }

    /**
     * Redoes the last undone command.
     *
     * @return "Redone: &lt;description&gt;" or "Nothing to redo."
     */
    public String redo() {
        Optional<String> description = history.nextRedoDescription();
        return history.redo() ? "Redone: " + description.orElseThrow() : "Nothing to redo.";
    }
}
