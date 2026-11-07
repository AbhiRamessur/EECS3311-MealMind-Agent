package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.ShoppingRow;
import com.mealmind.gui.logic.ShoppingRows;
import com.mealmind.gui.logic.TextExport;
import com.mealmind.shopping.ShoppingList;
import com.mealmind.shopping.ShoppingListFormatter;
import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * The Shopping List tab (F11): the current shopping list grouped by store section, refreshed on every
 * generator notification, with check-off boxes, "Copy as text" and "Export .txt". Check-off marks are kept
 * in this tab only for the session; they are not saved.
 */
final class ShoppingTab extends Tab {

    private final ApplicationContext ctx;
    private final BiConsumer<String, Boolean> status;
    private final TableView<ShoppingRow> table = new TableView<>();
    private final Label empty = new Label("Nothing to buy.");
    private final Label total = new Label();
    private final VBox warnings = new VBox(4);
    private Set<String> checked = new HashSet<>();

    ShoppingTab(ApplicationContext ctx, BiConsumer<String, Boolean> status) {
        super("Shopping List");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.status = Objects.requireNonNull(status, "status");
        setClosable(false);
        TableColumn<ShoppingRow, ShoppingRow> check = new TableColumn<>("Got it");
        check.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        check.setCellFactory(column -> new TableCell<>() {
            private final CheckBox box = new CheckBox();

            @Override
            protected void updateItem(ShoppingRow row, boolean isEmpty) {
                super.updateItem(row, isEmpty);
                if (isEmpty || row == null) {
                    setGraphic(null);
                    return;
                }
                box.setSelected(row.checked());
                box.setOnAction(e -> toggle(row));
                setGraphic(box);
            }
        });
        check.setPrefWidth(60);
        table.getColumns().add(check);
        table.getColumns().add(column("Aisle", ShoppingRow::aisle, 130));
        table.getColumns().add(column("Item", ShoppingRow::name, 200));
        table.getColumns().add(column("Quantity", ShoppingRow::quantityText, 100));
        table.getColumns().add(column("Cost", ShoppingRow::costText, 80));
        table.getColumns().add(column("Note", ShoppingRow::note, 220));
        table.setPlaceholder(new Label("Nothing to buy."));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(ShoppingRow row, boolean isEmpty) {
                super.updateItem(row, isEmpty);
                getStyleClass().remove("shopping-checked");
                if (!isEmpty && row != null && row.checked()) {
                    getStyleClass().add("shopping-checked");
                }
            }
        });
        Button copy = new Button("Copy as text");
        Button export = new Button("Export .txt");
        copy.setTooltip(new Tooltip("Copy the list to the clipboard as plain text"));
        export.setTooltip(new Tooltip("Save the list as a text file"));
        copy.setOnAction(e -> copy());
        export.setOnAction(e -> export());
        Label hint = new Label("Check-off marks are kept for this session only.");
        hint.getStyleClass().add("hint");
        VBox content = new VBox(8, table, empty, total, warnings, new HBox(8, copy, export), hint);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.getStyleClass().add("tab-content");
        setContent(content);
        refresh();
    }

    private static TableColumn<ShoppingRow, String> column(String title, Function<ShoppingRow, String> value, double width) {
        TableColumn<ShoppingRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /** Re-reads the current shopping list; called on generator notifications and after a plan replacement. */
    void refresh() {
        ShoppingList list = ctx.generator().getCurrent();
        checked = new HashSet<>(ShoppingRows.retainKnown(list, checked));
        table.getItems().setAll(ShoppingRows.of(list, checked));
        empty.setVisible(list.isEmpty());
        empty.setManaged(list.isEmpty());
        total.setText(ShoppingRows.totalText(list));
        warnings.getChildren().clear();
        ShoppingRows.warnings(list).forEach(w -> {
            Label label = new Label("! " + w);
            label.setWrapText(true);
            label.getStyleClass().add("hint");
            warnings.getChildren().add(label);
        });
    }

    /** Checks or unchecks an item (also used by the self-test). */
    void toggle(ShoppingRow row) {
        if (!checked.remove(row.key())) {
            checked.add(row.key());
        }
        refresh();
    }

    private void copy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(ShoppingListFormatter.toText(ctx.generator().getCurrent()));
        Clipboard.getSystemClipboard().setContent(content);
        status.accept("Copied the shopping list.", false);
    }

    private void export() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export shopping list");
        chooser.setInitialFileName(TextExport.defaultFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File file = chooser.showSaveDialog(getTabPane() == null ? null : getTabPane().getScene().getWindow());
        if (file != null) {
            exportTo(file.toPath());
        }
    }

    /** Writes the list to {@code file} (the dialog's choice, or a given file in the self-test). */
    void exportTo(Path file) {
        try {
            TextExport.write(file, ShoppingListFormatter.toText(ctx.generator().getCurrent()));
            status.accept("Exported to " + file.getFileName(), false);
        } catch (UncheckedIOException e) {
            status.accept(e.getMessage(), true);
        }
    }

    TableView<ShoppingRow> table() {
        return table;
    }

    Label totalLabel() {
        return total;
    }
}
