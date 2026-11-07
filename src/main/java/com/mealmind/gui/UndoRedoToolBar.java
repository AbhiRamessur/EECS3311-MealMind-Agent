package com.mealmind.gui;

import com.mealmind.gui.logic.UndoRedoModel;
import java.util.Objects;
import java.util.function.BiConsumer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;

/**
 * The Undo and Redo buttons. Labels, enabled state and tooltips come from {@link UndoRedoModel}; clicking
 * a button runs the shared command history and shows the CLI's message in the status bar.
 */
final class UndoRedoToolBar extends HBox {

    private final UndoRedoModel model;
    private final BiConsumer<String, Boolean> status;
    private final Button undoButton = new Button("Undo");
    private final Button redoButton = new Button("Redo");

    UndoRedoToolBar(UndoRedoModel model, BiConsumer<String, Boolean> status) {
        super(8);
        this.model = Objects.requireNonNull(model, "model");
        this.status = Objects.requireNonNull(status, "status");
        getStyleClass().add("toolbar");
        undoButton.setOnAction(e -> undo());
        redoButton.setOnAction(e -> redo());
        getChildren().addAll(undoButton, redoButton);
        refresh();
    }

    /** Ctrl+Z undoes and Ctrl+Y redoes anywhere in the window. */
    void installAccelerators(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), this::redo);
    }

    /** Re-reads the labels and enabled state; called on every command history change. */
    void refresh() {
        undoButton.setText(model.undoLabel());
        redoButton.setText(model.redoLabel());
        undoButton.setDisable(!model.canUndo());
        redoButton.setDisable(!model.canRedo());
        undoButton.setTooltip(new Tooltip(model.canUndo() ? model.undoLabel() + " (Ctrl+Z)" : "Nothing to undo"));
        redoButton.setTooltip(new Tooltip(model.canRedo() ? model.redoLabel() + " (Ctrl+Y)" : "Nothing to redo"));
    }

    private void undo() {
        status.accept(model.undo(), false);
    }

    private void redo() {
        status.accept(model.redo(), false);
    }

    Button undoButton() {
        return undoButton;
    }

    Button redoButton() {
        return redoButton;
    }
}
