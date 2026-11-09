package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.command.AddPantryItemCommand;
import com.mealmind.command.CommandHistory;
import com.mealmind.command.RemovePantryItemCommand;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.ExpiryLevel;
import com.mealmind.gui.logic.FieldError;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.PantryForm;
import com.mealmind.gui.logic.PantryRow;
import com.mealmind.gui.logic.PantryRows;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The Pantry tab: a table of {@link PantryRow}s (expired rows red, soon-to-expire rows amber), an add form
 * and a Remove Selected button. Adding and removing run {@link AddPantryItemCommand} and
 * {@link RemovePantryItemCommand} through the shared {@link CommandHistory}, so undo works as in the CLI.
 * Below them, the {@link LeftoversPane} ("Use up expiring items", F10) proposes meals for expiring items.
 */
final class PantryTab extends Tab {

    private final Pantry pantry;
    private final CommandHistory history;
    private final Supplier<LocalDate> today;
    private final BiConsumer<String, Boolean> status;
    private final TableView<PantryRow> table = new TableView<>();
    private final Label counts = new Label();
    private final TextField name = new TextField();
    private final TextField quantity = new TextField();
    private final ChoiceBox<Unit> unit = new ChoiceBox<>(FXCollections.observableArrayList(Unit.values()));
    private final DatePicker expiry = new DatePicker();
    private final Button add = new Button("Add");
    private final Button remove = new Button("Remove Selected");
    private final Map<String, Label> errors = Map.of("name", errorLabel(), "quantity", errorLabel(), "unit", errorLabel());
    private final LeftoversPane leftovers;

    PantryTab(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status, BusyFlag aiBusy) {
        super("Pantry");
        this.pantry = ctx.pantry();
        this.history = ctx.history();
        this.today = () -> LocalDate.now(ctx.clock());
        this.status = Objects.requireNonNull(status, "status");
        this.leftovers = new LeftoversPane(ctx, runner, ui, status, aiBusy);
        setClosable(false);

        table.getColumns().add(column("Name", PantryRow::name, 220));
        table.getColumns().add(column("Quantity", PantryRow::quantityText, 90));
        table.getColumns().add(column("Unit", PantryRow::unitSymbol, 60));
        table.getColumns().add(column("Expiry", PantryRow::expiryText, 110));
        table.getColumns().add(column("Status", PantryRow::statusText, 140));
        table.setPlaceholder(new Label("The pantry is empty."));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(PantryRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("expiry-expired", "expiry-soon");
                if (!empty && row != null && row.level() == ExpiryLevel.EXPIRED) {
                    getStyleClass().add("expiry-expired");
                } else if (!empty && row != null && row.level() == ExpiryLevel.SOON) {
                    getStyleClass().add("expiry-soon");
                }
            }
        });

        unit.setConverter(new StringConverter<>() {
            @Override
            public String toString(Unit value) {
                return value == null ? "" : value.getSymbol();
            }

            @Override
            public Unit fromString(String text) {
                return Unit.parse(text);
            }
        });
        unit.setValue(Unit.GRAM);
        name.setPromptText("e.g. Rice");
        quantity.setPromptText("e.g. 500");
        quantity.setPrefColumnCount(6);
        expiry.setPromptText("optional");
        add.setTooltip(new Tooltip("Add this item to the pantry (undoable)"));
        remove.setTooltip(new Tooltip("Remove the selected item (undoable)"));
        add.setOnAction(e -> addItem());
        remove.setOnAction(e -> removeSelected());
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        GridPane form = new GridPane();
        form.getStyleClass().add("form");
        form.setHgap(8);
        form.setVgap(4);
        form.addRow(0, label("Name", name), label("Quantity", quantity), label("Unit", unit), label("Expiry", expiry));
        form.addRow(1, name, quantity, unit, expiry, add);
        form.addRow(2, errors.get("name"), errors.get("quantity"), errors.get("unit"));

        VBox content = new VBox(10, table, counts, form, new HBox(8, remove), leftovers);
        content.getStyleClass().add("tab-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setContent(scroll);
        refresh();
    }

    private static TableColumn<PantryRow, String> column(String title, Function<PantryRow, String> value, double width) {
        TableColumn<PantryRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static Label label(String text, Node target) {
        Label label = new Label(text);
        label.setLabelFor(target);
        return label;
    }

    private static Label errorLabel() {
        Label label = new Label();
        label.getStyleClass().add("field-error");
        return label;
    }

    /** Reloads the table and counts; called on every pantry notification. */
    void refresh() {
        List<PantryRow> rows = PantryRows.of(pantry, today.get());
        table.getItems().setAll(rows);
        long urgent = rows.stream().filter(row -> row.level() != ExpiryLevel.NONE).count();
        counts.setText(rows.size() + (rows.size() == 1 ? " item" : " items") + ", " + urgent
                + " expired or expiring within " + PantryRows.WARNING_DAYS + " days");
    }

    private void addItem() {
        errors.values().forEach(label -> label.setText(""));
        FormResult<PantryItem> result = PantryForm.parse(name.getText(), quantity.getText(), unit.getValue(), expiry.getValue());
        if (!result.isValid()) {
            for (FieldError error : result.errors()) {
                errors.get(error.field()).setText(error.message());
            }
            status.accept("Item not added: " + result.errors().get(0).message(), true);
            return;
        }
        PantryItem item = result.value().orElseThrow();
        try {
            history.pushAndExecute(new AddPantryItemCommand(pantry, item));
        } catch (RuntimeException e) {
            status.accept("Item not added: " + e.getMessage(), true);
            return;
        }
        name.clear();
        quantity.clear();
        expiry.setValue(null);
        status.accept("Added " + item.getName() + ".", false);
    }

    private void removeSelected() {
        PantryRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            history.pushAndExecute(new RemovePantryItemCommand(pantry, selected.name()));
            status.accept("Removed " + selected.name() + ".", false);
        } catch (RuntimeException e) {
            status.accept("Item not removed: " + e.getMessage(), true);
        }
    }

    TableView<PantryRow> table() {
        return table;
    }

    TextField nameField() {
        return name;
    }

    TextField quantityField() {
        return quantity;
    }

    ChoiceBox<Unit> unitChoice() {
        return unit;
    }

    Button addButton() {
        return add;
    }

    DatePicker expiryPicker() {
        return expiry;
    }

    Label countsLabel() {
        return counts;
    }

    LeftoversPane leftovers() {
        return leftovers;
    }

    /** Closes the leftovers section's bindings. */
    void close() {
        leftovers.close();
    }
}
