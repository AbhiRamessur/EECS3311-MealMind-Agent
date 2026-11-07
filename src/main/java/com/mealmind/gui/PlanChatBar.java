package com.mealmind.gui;

import com.mealmind.agent.EditOutcome;
import com.mealmind.agent.PlanEditResult;
import com.mealmind.app.ApplicationContext;
import com.mealmind.command.CommandHistory;
import com.mealmind.gui.logic.AiAvailability;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.PlanEditForm;
import com.mealmind.gui.logic.PlanEditView;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.model.MealPlan;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Plan tab's "Ask the planner" bar (F06; Stage 1's chat sidebar). The AI only interprets the request;
 * the facade applies the change through an undoable command after re-checking safety. The job runs in the
 * {@link BackgroundRunner} and shares the {@link BusyFlag} with the Nutrition tab's AI button.
 */
final class PlanChatBar extends VBox {

    private final ApplicationContext ctx;
    private final BackgroundRunner runner;
    private final BiConsumer<String, Boolean> status;
    private final BusyFlag busy;
    private final TextField instruction = new TextField();
    private final Button send = new Button("Send");
    private final AiUnavailableLabel unavailable;
    private final AgentActivityPanel activity;
    private final Label result = new Label();

    PlanChatBar(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status, BusyFlag busy) {
        super(6);
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.status = Objects.requireNonNull(status, "status");
        this.busy = Objects.requireNonNull(busy, "busy");
        unavailable = new AiUnavailableLabel(ctx);
        activity = new AgentActivityPanel(ui);
        activity.log().setPrefRowCount(4);
        instruction.setPromptText("e.g. Swap Tuesday dinner for a 15-minute meal (Day 1 is Monday)");
        HBox.setHgrow(instruction, Priority.ALWAYS);
        Label label = new Label("Ask the planner");
        label.setLabelFor(instruction);
        send.setOnAction(e -> send());
        result.setWrapText(true);
        getChildren().addAll(new HBox(8, label, instruction, send, unavailable), result, activity);
        busy.addListener(this::refreshAi);
        refreshAi();
    }

    /** Enables Send only when AI is available and no AI job is running. */
    void refreshAi() {
        unavailable.update();
        String reason = AiAvailability.disabledReason(ctx);
        send.setDisable(busy.busy() || !reason.isEmpty());
        send.setTooltip(new Tooltip(reason.isEmpty() ? "Ask the AI to make one change to the plan (paid API calls)"
                : AiAvailability.tooltip(reason)));
    }

    private void send() {
        FormResult<String> parsed = PlanEditForm.parseInstruction(instruction.getText());
        if (!parsed.isValid()) {
            status.accept(parsed.errors().get(0).message(), true);
            return;
        }
        String request = parsed.value().orElseThrow();
        // Read on the FX thread: the current plan and history at the moment of the request.
        MealPlan plan = ctx.plan();
        CommandHistory history = ctx.history();
        activity.model().clear();
        busy.set(true);
        runner.submit(() -> ctx.agent().editPlan(request, plan, history, AgentActivityPanel.forwarder(activity.model())),
                edit -> {
                    busy.set(false);
                    show(edit);
                },
                error -> {
                    busy.set(false);
                    status.accept(error.getMessage() == null ? String.valueOf(error) : error.getMessage(), true);
                });
    }

    private void show(PlanEditResult edit) {
        List<String> lines = PlanEditView.describe(edit);
        result.setText(String.join("\n", lines));
        activity.model().addNote(PlanEditView.statsLine(edit));
        status.accept(lines.get(0), edit.outcome() == EditOutcome.REFUSED);
    }

    TextField instructionField() {
        return instruction;
    }

    Button sendButton() {
        return send;
    }

    AiUnavailableLabel unavailableLabel() {
        return unavailable;
    }

    Label resultLabel() {
        return result;
    }

    AgentActivityPanel activity() {
        return activity;
    }
}
