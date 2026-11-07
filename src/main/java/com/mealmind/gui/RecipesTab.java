package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.RecipeRow;
import com.mealmind.gui.logic.RecipeRows;
import com.mealmind.gui.logic.UiThread;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Recipes tab (F03, F04, F08, F12): a filterable table of every recipe on the left (unsafe recipes are
 * listed but flagged) and, on the right, the Details, Recommendations and Generate sections. It uses the
 * same recipe book, preferences, commands and agent facade as the CLI; {@link MainController} subscribes
 * it to their notifications.
 */
final class RecipesTab extends Tab {

    private final ApplicationContext ctx;
    private final TextField filter = new TextField();
    private final CheckBox favoritesOnly = new CheckBox("Favorites only");
    private final TableView<RecipeRow> table = new TableView<>();
    private final RecipeDetailsPane details;
    private final RecommendPane recommend;
    private final GeneratePane generate;

    RecipesTab(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status) {
        super("Recipes");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        setClosable(false);
        BusyFlag busy = new BusyFlag();
        details = new RecipeDetailsPane(ctx, status);
        recommend = new RecommendPane(ctx, runner, ui, status, busy, this::select);
        generate = new GeneratePane(ctx, runner, ui, status, busy);

        filter.setPromptText("Filter by id, name or ingredient");
        filter.textProperty().addListener((obs, old, now) -> refresh());
        favoritesOnly.setOnAction(e -> refresh());
        table.getColumns().add(column("Id", RecipeRow::id, 60));
        table.getColumns().add(column("Name", RecipeRow::name, 210));
        table.getColumns().add(column("Servings", RecipeRow::servingsText, 70));
        table.getColumns().add(column("Prep", RecipeRow::prepText, 65));
        table.getColumns().add(column("kcal/serving", RecipeRow::caloriesText, 90));
        table.getColumns().add(column("Rating", RecipeRow::ratingText, 60));
        table.getColumns().add(column("Fav", row -> row.favorite() ? "yes" : "", 45));
        table.getColumns().add(column("Safe", row -> row.safe() ? "yes" : "NO", 50));
        table.setPlaceholder(new Label("No recipes match."));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(RecipeRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("recipe-unsafe", "recipe-favorite");
                if (!empty && row != null && !row.safe()) {
                    getStyleClass().add("recipe-unsafe");
                } else if (!empty && row != null && row.favorite()) {
                    getStyleClass().add("recipe-favorite");
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> details.show(row == null ? null : row.id()));

        Label filterLabel = new Label("Filter");
        filterLabel.setLabelFor(filter);
        HBox.setHgrow(filter, Priority.ALWAYS);
        VBox left = new VBox(8, new HBox(8, filterLabel, filter, favoritesOnly), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        left.getStyleClass().add("tab-content");
        TabPane sections = new TabPane(new Tab("Details", details), new Tab("Recommendations", recommend),
                new Tab("Generate", generate));
        sections.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        SplitPane split = new SplitPane(left, sections);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.48);
        setContent(split);
        refresh();
    }

    private static TableColumn<RecipeRow, String> column(String title, Function<RecipeRow, String> value, double width) {
        TableColumn<RecipeRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /** Reloads the table (keeping the selection) and the details; called on recipe, preference and profile changes. */
    void refresh() {
        String selected = details.recipeId();
        table.getItems().setAll(RecipeRows.of(ctx.recipeBook(), ctx.preferences(), ctx.validator(), ctx::isBundledRecipe,
                favoritesOnly.isSelected(), filter.getText()));
        if (selected != null && !select(selected)) {
            details.show(null);
        } else if (selected != null) {
            details.show(selected);
        }
    }

    /** Re-reads the pending recipe; called on agent facade notifications. */
    void refreshPending() {
        generate.refresh();
    }

    /** Re-reads AI availability; called on AI service notifications. */
    void refreshAi() {
        generate.refreshAi();
        recommend.refreshAi();
    }

    /** Selects the row with {@code id}; returns false if it is not shown. */
    boolean select(String id) {
        for (RecipeRow row : table.getItems()) {
            if (row.id().equals(id)) {
                table.getSelectionModel().select(row);
                return true;
            }
        }
        return false;
    }

    TableView<RecipeRow> table() {
        return table;
    }

    TextField filterField() {
        return filter;
    }

    CheckBox favoritesOnlyBox() {
        return favoritesOnly;
    }

    RecipeDetailsPane details() {
        return details;
    }

    RecommendPane recommend() {
        return recommend;
    }

    GeneratePane generate() {
        return generate;
    }
}
