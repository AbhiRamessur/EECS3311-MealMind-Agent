package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.AiAvailability;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.FieldError;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.LeftoverForm;
import com.mealmind.gui.logic.LeftoverRow;
import com.mealmind.gui.logic.LeftoverRows;
import com.mealmind.gui.logic.LeftoverSession;
import com.mealmind.gui.logic.LeftoverView;
import com.mealmind.gui.logic.ObserverBinding;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.leftovers.LeftoverCandidates;
import com.mealmind.leftovers.LeftoverFlow;
import com.mealmind.leftovers.LeftoverPlanner;
import com.mealmind.leftovers.LeftoverProposal;
import com.mealmind.leftovers.LeftoverRequest;
import com.mealmind.model.MealPlan;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The Pantry tab's "Use up expiring items" section (F10 in the GUI; Stage 1's "Clear Expiring Items" button and
 * proposal modal, shown inline). A thin view over {@link LeftoverFlow} and {@link LeftoverPlanner}, the same
 * code the CLI's {@code plan leftovers} uses: Find meals shows the deterministic proposal at once (it is fast,
 * so it runs on the FX thread); the optional AI re-ranking runs in the {@link BackgroundRunner} under the shared
 * AI {@link BusyFlag}; Apply goes only through {@link LeftoverPlanner#apply}, one undoable command.
 */
final class LeftoversPane extends TitledPane {

    private final ApplicationContext ctx;
    private final BackgroundRunner runner;
    private final BiConsumer<String, Boolean> status;
    private final BusyFlag busy;
    private final LeftoverSession session = new LeftoverSession();
    private final List<ObserverBinding> bindings = new ArrayList<>();
    private final TextField days = new TextField("3");
    private final TextField meals = new TextField("3");
    private final CheckBox overwrite = new CheckBox("Replace existing meals if no slot is free");
    private final CheckBox rerank = new CheckBox("Re-rank with AI");
    private final AiUnavailableLabel unavailable;
    private final Button find = new Button("Find meals");
    private final Button apply = new Button("Apply");
    private final Button discard = new Button("Discard");
    private final Label hint = new Label();
    private final Label message = new Label();
    private final Label expiring = new Label();
    private final Label notes = new Label();
    private final Label title = new Label(LeftoverView.proposalTitle());
    private final Label daysError = errorLabel();
    private final Label mealsError = errorLabel();
    private final TableView<LeftoverRow> table = new TableView<>();
    private final ProgressIndicator working = new ProgressIndicator();
    private final AgentActivityPanel activity;
    private boolean running;

    LeftoversPane(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status, BusyFlag busy) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.status = Objects.requireNonNull(status, "status");
        this.busy = Objects.requireNonNull(busy, "busy");
        setText("Use up expiring items");
        setCollapsible(false);
        unavailable = new AiUnavailableLabel(ctx);
        activity = new AgentActivityPanel(ui);
        activity.log().setPrefRowCount(4);
        show(activity, false);

        days.setPrefColumnCount(4);
        meals.setPrefColumnCount(4);
        days.textProperty().addListener((obs, old, now) -> refreshHint());
        find.setTooltip(new Tooltip("Propose recipes from your book that use items expiring soon (nothing changes yet)"));
        apply.setTooltip(new Tooltip("Add every proposed meal to the plan as one undoable step"));
        discard.setTooltip(new Tooltip("Drop the proposal"));
        overwrite.setTooltip(new Tooltip("When the plan has no empty slots, proposed meals may replace existing ones"));
        find.setOnAction(e -> find());
        apply.setOnAction(e -> apply());
        discard.setOnAction(e -> discard());
        hint.getStyleClass().add("leftover-hint");
        title.getStyleClass().add("leftover-pending");
        message.setWrapText(true);
        notes.setWrapText(true);
        working.setPrefSize(18, 18);
        show(working, false);

        table.getColumns().add(column("Slot", LeftoverRow::slotText, 170));
        table.getColumns().add(column("Recipe", LeftoverRow::recipeText, 200));
        table.getColumns().add(column("Why", LeftoverRow::reasonText, 280));
        table.getColumns().add(column("Replaces", LeftoverRow::replacesText, 200));
        table.setPlaceholder(new Label("No proposal yet. Press Find meals."));
        table.setPrefHeight(150);

        GridPane form = new GridPane();
        form.getStyleClass().add("form");
        form.setHgap(8);
        form.setVgap(4);
        form.addRow(0, labelled("Days", days), labelled("Meals", meals));
        form.addRow(1, days, meals, overwrite);
        form.addRow(2, daysError, mealsError);
        HBox actions = new HBox(8, find, rerank, unavailable, working);
        VBox content = new VBox(8, hint, form, actions, message, expiring, notes, title, table, new HBox(8, apply, discard), activity);
        setContent(content);

        bindings.add(ObserverBinding.bind(ctx.pantry(), ui, this::refreshHint));
        bindings.add(ObserverBinding.bind(ctx.llm(), ui, this::refreshAi));
        busy.addListener(this::updateControls);
        clearProposalView();
        refreshHint();
        refreshAi();
    }

    private static TableColumn<LeftoverRow, String> column(String name, Function<LeftoverRow, String> value, double width) {
        TableColumn<LeftoverRow, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static Label labelled(String text, TextField field) {
        Label label = new Label(text);
        label.setLabelFor(field);
        return label;
    }

    private static Label errorLabel() {
        Label label = new Label();
        label.getStyleClass().add("field-error");
        return label;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private LocalDate today() {
        return LocalDate.now(ctx.clock());
    }

    /** Built on every use, because the plan and the profile can change between uses. */
    private LeftoverPlanner planner() {
        return new LeftoverPlanner(ctx.recipeBook(), ctx.pantry(), ctx.validator(), ctx.profile(), ctx.preferences());
    }

    /** Updates the "N items expire within D days" hint; called on pantry notifications and when Days changes. */
    void refreshHint() {
        FormResult<LeftoverRequest> parsed = LeftoverForm.parse(days.getText(), "1", false);
        int window = parsed.value().map(LeftoverRequest::withinDays).orElse(LeftoverRequest.defaults().withinDays());
        hint.setText(LeftoverView.expiringHint(ctx.pantry().getExpiringItems(today(), window).size(), window));
    }

    /** Re-reads AI availability; called on AI service notifications. */
    void refreshAi() {
        String reason = AiAvailability.disabledReason(ctx);
        unavailable.update();
        rerank.setTooltip(new Tooltip(reason.isEmpty()
                ? "Let the AI reorder the safe recipes found (paid API call); it cannot add recipes"
                : AiAvailability.tooltip(reason)));
        if (!reason.isEmpty()) {
            rerank.setSelected(false);
        }
        updateControls();
    }

    private void updateControls() {
        boolean blocked = busy.busy();
        find.setDisable(blocked);
        apply.setDisable(blocked || !session.isPending());
        discard.setDisable(blocked || !session.isPending());
        rerank.setDisable(blocked || !AiAvailability.disabledReason(ctx).isEmpty());
        show(working, running);
    }

    private void find() {
        daysError.setText("");
        mealsError.setText("");
        FormResult<LeftoverRequest> parsed = LeftoverForm.parse(days.getText(), meals.getText(), overwrite.isSelected());
        if (!parsed.isValid()) {
            for (FieldError error : parsed.errors()) {
                (error.field().equals("days") ? daysError : mealsError).setText(error.message());
            }
            status.accept("Leftover search not run: " + parsed.errors().get(0).message(), true);
            return;
        }
        LeftoverRequest request = parsed.value().orElseThrow();
        session.clear();
        clearProposalView();
        activity.model().clear();
        LeftoverPlanner planner = planner();
        MealPlan plan = ctx.plan();
        LeftoverFlow.Outcome outcome = LeftoverFlow.propose(planner, plan, request, today());
        if (outcome.blocked().isPresent()) {
            message.setText(outcome.blocked().get());
            status.accept(outcome.blocked().get(), false);
            updateControls();
            return;
        }
        LeftoverCandidates candidates = outcome.candidates().orElseThrow();
        boolean useAi = rerank.isSelected() && AiAvailability.disabledReason(ctx).isEmpty();
        if (useAi && !LeftoverFlow.canRerank(candidates)) {
            // No AI call is made with a single candidate; this only adds the CLI's note.
            outcome = LeftoverFlow.rerank(planner, candidates, plan, request, ctx.agent(), null);
        }
        showProposal(outcome, request);
        status.accept(outcome.proposal().orElseThrow().assignments().size() + " meal(s) proposed; nothing applied yet.", false);
        if (useAi && LeftoverFlow.canRerank(candidates)) {
            startRerank(planner, candidates, plan, request);
        }
    }

    private void startRerank(LeftoverPlanner planner, LeftoverCandidates candidates, MealPlan plan, LeftoverRequest request) {
        show(activity, true);
        running = true;
        busy.set(true);
        updateControls();
        runner.submit(() -> LeftoverFlow.rerank(planner, candidates, plan, request, ctx.agent(),
                        AgentActivityPanel.forwarder(activity.model())),
                outcome -> {
                    finishRerank();
                    if (outcome.aiProblem().isPresent()) {
                        status.accept(LeftoverView.aiProblem(outcome.aiProblem().get()), false);
                    } else {
                        showProposal(outcome, request);
                        status.accept("AI re-ranked the proposal; nothing applied yet.", false);
                    }
                },
                error -> {
                    finishRerank();
                    status.accept(LeftoverView.aiProblem(error.getMessage() == null ? String.valueOf(error) : error.getMessage()), false);
                });
    }

    private void finishRerank() {
        running = false;
        busy.set(false);
        updateControls();
    }

    private void showProposal(LeftoverFlow.Outcome outcome, LeftoverRequest request) {
        LeftoverProposal proposal = outcome.proposal().orElseThrow();
        session.set(proposal, request);
        List<String> header = LeftoverView.headerLines(proposal, request, outcome.notes());
        message.setText("");
        expiring.setText(header.get(0));
        notes.setText(String.join("\n", header.subList(1, header.size())));
        show(title, true);
        table.getItems().setAll(LeftoverRows.of(proposal));
        updateControls();
    }

    private void clearProposalView() {
        expiring.setText("");
        notes.setText("");
        show(title, false);
        table.getItems().clear();
        updateControls();
    }

    private void apply() {
        LeftoverProposal proposal = session.proposal().orElse(null);
        if (proposal == null) {
            return;
        }
        List<String> lines;
        try {
            lines = planner().apply(proposal, ctx.plan(), ctx.history());
        } catch (IllegalStateException e) {
            // As in the CLI: a proposal for a replaced plan is dropped; a safety refusal keeps it.
            if (session.isStaleFor(ctx.plan())) {
                session.clear();
                clearProposalView();
            }
            status.accept(e.getMessage(), true);
            updateControls();
            return;
        }
        session.clear();
        clearProposalView();
        String description = ctx.history().nextUndoDescription().orElseThrow();
        status.accept(LeftoverView.applyMessage(description) + " " + String.join("; ", lines) + ". "
                + LeftoverView.applyHint(), false);
    }

    private void discard() {
        session.clear();
        clearProposalView();
        message.setText("");
        status.accept(LeftoverFlow.DISCARDED, false);
    }

    /** Unsubscribes the pantry and AI bindings. */
    void close() {
        bindings.forEach(ObserverBinding::close);
        bindings.clear();
    }

    TextField daysField() {
        return days;
    }

    TextField mealsField() {
        return meals;
    }

    CheckBox overwriteBox() {
        return overwrite;
    }

    CheckBox rerankBox() {
        return rerank;
    }

    AiUnavailableLabel unavailableLabel() {
        return unavailable;
    }

    Button findButton() {
        return find;
    }

    Button applyButton() {
        return apply;
    }

    Button discardButton() {
        return discard;
    }

    TableView<LeftoverRow> table() {
        return table;
    }

    Label messageLabel() {
        return message;
    }

    Label expiringLabel() {
        return expiring;
    }

    Label notesLabel() {
        return notes;
    }

    Label hintLabel() {
        return hint;
    }

    Label daysError() {
        return daysError;
    }

    Label mealsError() {
        return mealsError;
    }

    AgentActivityPanel activity() {
        return activity;
    }

    LeftoverSession session() {
        return session;
    }
}
