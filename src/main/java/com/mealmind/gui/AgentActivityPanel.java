package com.mealmind.gui;

import com.mealmind.agent.AgentEvent;
import com.mealmind.gui.logic.AgentActivityModel;
import com.mealmind.gui.logic.UiThread;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The Stage 1 "Agent Activity live log panel": a read-only, auto-scrolling log of "[agent] ..." lines over
 * an {@link AgentActivityModel}. Reusable by any tab that runs the agent.
 */
final class AgentActivityPanel extends VBox {

    private final TextArea log = new TextArea();
    private final AgentActivityModel model;

    AgentActivityPanel(UiThread ui) {
        super(6);
        model = new AgentActivityModel(Objects.requireNonNull(ui, "ui"), this::render, AgentActivityModel.DEFAULT_MAX_LINES);
        Label title = new Label("Agent activity");
        title.setLabelFor(log);
        Button clear = new Button("Clear");
        clear.setTooltip(new Tooltip("Clear the activity log"));
        clear.setOnAction(e -> model.clear());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        log.setEditable(false);
        log.setWrapText(false);
        log.setPrefRowCount(8);
        log.getStyleClass().add("activity-log");
        getChildren().addAll(new HBox(8, title, spacer, clear), log);
        VBox.setVgrow(log, Priority.ALWAYS);
    }

    /**
     * Returns a consumer that forwards agent events to the model; safe to call from any thread.
     *
     * @param model the activity model
     * @return the consumer to pass to the agent facade
     */
    static Consumer<AgentEvent> forwarder(AgentActivityModel model) {
        Objects.requireNonNull(model, "model");
        return model::accept;
    }

    AgentActivityModel model() {
        return model;
    }

    TextArea log() {
        return log;
    }

    /** Shows the model's lines and scrolls to the end. Runs on the FX thread (through the model's UiThread). */
    private void render() {
        log.setText(String.join("\n", model.lines()));
        log.positionCaret(log.getLength());
        log.setScrollTop(Double.MAX_VALUE);
    }
}
