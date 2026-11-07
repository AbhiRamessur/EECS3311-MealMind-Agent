package com.mealmind.gui;

import com.mealmind.agent.AgentException;
import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.AiAvailability;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.RecommendForm;
import com.mealmind.gui.logic.RecommendRow;
import com.mealmind.gui.logic.RecommendRows;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.llm.LlmException;
import com.mealmind.recommend.RecommendationRequest;
import com.mealmind.recommend.RecommendationResult;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Recipes tab's Recommendations section (F03). The deterministic list from the recommender is shown at
 * once; with "Explain with AI" the facade's re-ranking runs in the {@link BackgroundRunner} and replaces it
 * on success. A failed or unavailable AI keeps the deterministic list.
 */
final class RecommendPane extends VBox {

    private final ApplicationContext ctx;
    private final BackgroundRunner runner;
    private final BiConsumer<String, Boolean> status;
    private final BusyFlag busy;
    private final CheckBox pantryFirst = new CheckBox("Pantry first");
    private final TextField maxMinutes = new TextField();
    private final TextField limit = new TextField(RecommendForm.DEFAULT_LIMIT);
    private final CheckBox explain = new CheckBox("Explain with AI");
    private final Button recommend = new Button("Recommend");
    private final Label heading = new Label("Recommendations");
    private final Label notes = new Label();
    private final TableView<RecommendRow> table = new TableView<>();
    private final AgentActivityPanel activity;

    RecommendPane(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status,
                  BusyFlag busy, Consumer<String> onPick) {
        super(8);
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.status = Objects.requireNonNull(status, "status");
        this.busy = Objects.requireNonNull(busy, "busy");
        activity = new AgentActivityPanel(ui);
        maxMinutes.setPromptText("any");
        maxMinutes.setPrefColumnCount(4);
        limit.setPrefColumnCount(3);
        recommend.setTooltip(new Tooltip("Recommend safe recipes from the recipe book"));
        recommend.setOnAction(e -> recommend());
        notes.setWrapText(true);
        notes.getStyleClass().add("hint");
        table.getColumns().add(column("#", RecommendRow::rank, 40));
        table.getColumns().add(column("Id", RecommendRow::id, 60));
        table.getColumns().add(column("Name", RecommendRow::name, 200));
        table.getColumns().add(column("Why", RecommendRow::reason, 420));
        table.setPlaceholder(new Label("Press Recommend."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) {
                onPick.accept(row.id());
            }
        });
        Label maxLabel = new Label("Max minutes");
        maxLabel.setLabelFor(maxMinutes);
        Label limitLabel = new Label("Limit");
        limitLabel.setLabelFor(limit);
        getChildren().addAll(new HBox(8, pantryFirst, maxLabel, maxMinutes, limitLabel, limit, explain, recommend),
                heading, notes, table, activity);
        VBox.setVgrow(table, Priority.ALWAYS);
        getStyleClass().add("tab-content");
        busy.addListener(this::updateControls);
        refreshAi();
    }

    private static TableColumn<RecommendRow, String> column(String title, Function<RecommendRow, String> value, double width) {
        TableColumn<RecommendRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /** Enables or disables "Explain with AI" from the current AI availability. */
    void refreshAi() {
        String reason = AiAvailability.disabledReason(ctx);
        if (!reason.isEmpty()) {
            explain.setSelected(false);
        }
        explain.setTooltip(new Tooltip(reason.isEmpty() ? "Re-rank the list with AI (paid API call)"
                : AiAvailability.tooltip(reason)));
        updateControls();
    }

    private void updateControls() {
        explain.setDisable(busy.busy() || !AiAvailability.disabledReason(ctx).isEmpty());
        recommend.setDisable(busy.busy());
    }

    private void recommend() {
        FormResult<RecommendationRequest> request = RecommendForm.parse(pantryFirst.isSelected(), maxMinutes.getText(), limit.getText());
        if (!request.isValid()) {
            status.accept("Not recommended: " + request.errors().get(0).field() + " " + request.errors().get(0).message(), true);
            return;
        }
        RecommendationResult result;
        try {
            result = ctx.recommender().recommend(request.value().orElseThrow());
        } catch (IllegalStateException e) {
            status.accept(e.getMessage(), true);
            return;
        }
        show(result, "Recommendations");
        status.accept(result.items().size() + " recommendations.", false);
        if (explain.isSelected() && result.items().size() >= 2) {
            explainWithAi(result);
        }
    }

    private void explainWithAi(RecommendationResult result) {
        activity.model().clear();
        busy.set(true);
        runner.submit(() -> ctx.agent().rerankRecommendations(result, AgentActivityPanel.forwarder(activity.model())),
                ranked -> {
                    busy.set(false);
                    show(ranked, "AI-ranked");
                    status.accept("Recommendations ranked with AI assistance.", false);
                },
                error -> {
                    busy.set(false);
                    String message = error instanceof LlmException || error instanceof AgentException
                            || error instanceof IllegalStateException ? error.getMessage() : String.valueOf(error);
                    status.accept("AI explanation unavailable: " + message, true);
                });
    }

    private void show(RecommendationResult result, String title) {
        heading.setText(title);
        notes.setText(String.join("\n", RecommendRows.notes(result)));
        table.getItems().setAll(RecommendRows.of(result));
    }

    CheckBox pantryFirstBox() {
        return pantryFirst;
    }

    CheckBox explainBox() {
        return explain;
    }

    Button recommendButton() {
        return recommend;
    }

    TableView<RecommendRow> table() {
        return table;
    }

    Label heading() {
        return heading;
    }

    AgentActivityPanel activity() {
        return activity;
    }
}
