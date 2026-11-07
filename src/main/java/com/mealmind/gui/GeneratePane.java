package com.mealmind.gui;

import com.mealmind.agent.GeneratedRecipe;
import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.AgentActivityModel;
import com.mealmind.gui.logic.AiAvailability;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.GenerationModel;
import com.mealmind.gui.logic.PendingView;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.model.Recipe;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Recipes tab's Generate section (F04): a prompt bar, the agent activity log and an in-tab preview of
 * the pending recipe with Save and Discard. Generation runs in the {@link BackgroundRunner}, never on the FX
 * thread. The candidate is only pending until Save, which goes through the facade's re-validating
 * {@code savePendingRecipe}; Save is disabled for an unsafe candidate.
 */
final class GeneratePane extends VBox {

    private final ApplicationContext ctx;
    private final BackgroundRunner runner;
    private final BiConsumer<String, Boolean> status;
    private final BusyFlag busy;
    private final TextField prompt = new TextField();
    private final Button generate = new Button("Generate");
    private final HBox working = new HBox(6, new ProgressIndicator(), new Label("Working..."));
    private final AgentActivityPanel activity;
    private final Label title = new Label();
    private final TextArea preview = new TextArea();
    private final Label banner = new Label();
    private final Button save = new Button("Save");
    private final Button discard = new Button("Discard");
    private PendingView view = GenerationModel.describePending(Optional.empty());

    GeneratePane(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status, BusyFlag busy) {
        super(8);
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.status = Objects.requireNonNull(status, "status");
        this.busy = Objects.requireNonNull(busy, "busy");
        activity = new AgentActivityPanel(ui);
        prompt.setPromptText("Describe the recipe you want");
        HBox.setHgrow(prompt, Priority.ALWAYS);
        Label promptLabel = new Label("Request");
        promptLabel.setLabelFor(prompt);
        ((ProgressIndicator) working.getChildren().get(0)).setPrefSize(18, 18);
        working.setVisible(false);
        generate.setOnAction(e -> generate());
        save.setTooltip(new Tooltip("Save the pending recipe to your recipe book (undoable)"));
        discard.setTooltip(new Tooltip("Discard the pending recipe"));
        save.setOnAction(e -> save());
        discard.setOnAction(e -> discard());
        title.getStyleClass().add("detail-title");
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(10);
        banner.setWrapText(true);
        getChildren().addAll(new HBox(8, promptLabel, prompt, generate, working), activity, title, preview, banner,
                new HBox(8, save, discard));
        VBox.setVgrow(preview, Priority.ALWAYS);
        getStyleClass().add("tab-content");
        busy.addListener(this::updateControls);
        refresh();
        refreshAi();
    }

    /** Re-reads the facade's pending recipe; called on every facade notification. */
    void refresh() {
        view = GenerationModel.describePending(ctx.agent().pendingRecipe());
        title.setText(view.title());
        preview.setText(String.join("\n", view.lines()));
        banner.setText(view.safetyBanner());
        banner.getStyleClass().removeAll("banner-ok", "banner-warning");
        if (view.present()) {
            banner.getStyleClass().add(view.safe() ? "banner-ok" : "banner-warning");
        }
        updateControls();
    }

    /** Enables or disables Generate from the current AI availability. */
    void refreshAi() {
        String reason = AiAvailability.disabledReason(ctx);
        generate.setTooltip(new Tooltip(reason.isEmpty() ? "Ask the AI agent for a recipe (paid API calls)"
                : AiAvailability.tooltip(reason)));
        updateControls();
    }

    private void updateControls() {
        boolean running = busy.busy();
        generate.setDisable(running || !AiAvailability.disabledReason(ctx).isEmpty());
        working.setVisible(running);
        save.setDisable(running || !view.present() || !view.safe());
        discard.setDisable(running || !view.present());
    }

    private void generate() {
        FormResult<String> parsed = GenerationModel.parsePrompt(prompt.getText());
        if (!parsed.isValid()) {
            status.accept(parsed.errors().get(0).message(), true);
            return;
        }
        String request = parsed.value().orElseThrow();
        AgentActivityModel log = activity.model();
        log.clear();
        busy.set(true);
        runner.submit(() -> ctx.agent().generateRecipe(request, AgentActivityPanel.forwarder(log)),
                result -> {
                    busy.set(false);
                    log.addNote(statsLine(result));
                    refresh();
                    status.accept("Pending recipe " + result.recipe().getId() + " (not saved)", false);
                },
                error -> {
                    busy.set(false);
                    status.accept(error.getMessage() == null ? String.valueOf(error) : error.getMessage(), true);
                });
    }

    private static String statsLine(GeneratedRecipe result) {
        return AgentActivityModel.statsLine(result.llmCalls(), result.toolSteps(), result.repairs(), result.safetyRetries(),
                result.usage());
    }

    private void save() {
        try {
            Recipe saved = ctx.agent().savePendingRecipe(ctx.history());
            status.accept("Saved " + saved.getName() + " as " + saved.getId() + ". Use Undo to remove it.", false);
        } catch (RuntimeException e) {
            status.accept(e.getMessage(), true);
        }
    }

    private void discard() {
        if (ctx.agent().discardPendingRecipe()) {
            status.accept("Discarded the pending recipe.", false);
        }
    }

    TextField promptField() {
        return prompt;
    }

    Button generateButton() {
        return generate;
    }

    Button saveButton() {
        return save;
    }

    Button discardButton() {
        return discard;
    }

    Label titleLabel() {
        return title;
    }

    Label banner() {
        return banner;
    }

    AgentActivityPanel activity() {
        return activity;
    }
}
