package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.AiAvailability;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.NutritionView;
import com.mealmind.gui.logic.NutritionViewModel;
import com.mealmind.gui.logic.ProgressBarRow;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.nutrition.NutritionSummary;
import java.util.Objects;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The Nutrition tab (F09): the computed figures as {@code plan nutrition} prints them, one progress bar per
 * target set in the profile, and an "AI nutrition assessment" section whose commentary only interprets the
 * figures. It refreshes on plan and profile notifications; the numbers stay on screen if the AI fails.
 */
final class NutritionTab extends Tab {

    private final ApplicationContext ctx;
    private final BackgroundRunner runner;
    private final BiConsumer<String, Boolean> status;
    private final BusyFlag busy;
    private final VBox figures = new VBox(4);
    private final VBox bars = new VBox(6);
    private final Button explain = new Button("Explain with AI");
    private final AiUnavailableLabel unavailable;
    private final TextArea commentary = new TextArea();
    private final AgentActivityPanel activity;
    private NutritionViewModel view;

    NutritionTab(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status, BusyFlag busy) {
        super("Nutrition");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.status = Objects.requireNonNull(status, "status");
        this.busy = Objects.requireNonNull(busy, "busy");
        setClosable(false);
        unavailable = new AiUnavailableLabel(ctx);
        activity = new AgentActivityPanel(ui);
        activity.log().setPrefRowCount(4);
        commentary.setEditable(false);
        commentary.setWrapText(true);
        commentary.setPrefRowCount(4);
        explain.setOnAction(e -> explain());
        Label title = new Label("AI nutrition assessment");
        title.getStyleClass().add("detail-title");
        title.setLabelFor(commentary);
        VBox content = new VBox(10, figures, bars, title, new HBox(8, explain, unavailable), commentary, activity);
        content.getStyleClass().add("tab-content");
        setContent(new ScrollPane(content));
        busy.addListener(this::updateControls);
        refresh();
    }

    /** Re-reads the current plan's nutrition; called on plan and profile notifications. */
    void refresh() {
        view = NutritionView.of(ctx.nutrition());
        figures.getChildren().clear();
        bars.getChildren().clear();
        if (view.empty()) {
            figures.getChildren().add(new Label(NutritionView.EMPTY_TEXT));
        } else {
            view.dayLines().forEach(line -> figures.getChildren().add(new Label(line)));
            figures.getChildren().addAll(new Label(view.totalsLine()), new Label(view.averageLine()));
            for (ProgressBarRow row : view.progress()) {
                ProgressBar bar = new ProgressBar(Math.min(row.fraction(), 1.0));
                bar.setPrefWidth(260);
                bar.getStyleClass().add(row.over() ? "progress-over" : "progress-ok");
                Label text = new Label(row.text());
                text.setLabelFor(bar);
                bars.getChildren().add(new HBox(10, bar, text));
            }
            if (!view.unknownLine().isEmpty()) {
                Label unknown = new Label(view.unknownLine());
                unknown.setWrapText(true);
                unknown.getStyleClass().add("hint");
                figures.getChildren().add(unknown);
            }
        }
        refreshAi();
    }

    /** Re-reads AI availability; called on AI service notifications. */
    void refreshAi() {
        unavailable.update();
        explain.setTooltip(new Tooltip("Ask the AI to comment on these figures (paid API call)"));
        updateControls();
    }

    private void updateControls() {
        explain.setDisable(busy.busy() || view == null || view.empty() || !AiAvailability.disabledReason(ctx).isEmpty());
    }

    private void explain() {
        NutritionSummary summary = ctx.nutrition();
        activity.model().clear();
        busy.set(true);
        runner.submit(() -> ctx.agent().explainNutrition(summary, AgentActivityPanel.forwarder(activity.model())),
                text -> {
                    busy.set(false);
                    commentary.setText("AI commentary: " + text);
                    status.accept("AI commentary ready.", false);
                },
                error -> {
                    busy.set(false);
                    String message = "AI explanation unavailable: "
                            + (error.getMessage() == null ? String.valueOf(error) : error.getMessage());
                    commentary.setText(message);
                    status.accept(message, true);
                });
    }

    VBox figures() {
        return figures;
    }

    VBox bars() {
        return bars;
    }

    Button explainButton() {
        return explain;
    }

    AiUnavailableLabel unavailableLabel() {
        return unavailable;
    }

    TextArea commentary() {
        return commentary;
    }
}
