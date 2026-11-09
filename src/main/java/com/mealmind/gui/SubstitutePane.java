package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.AiAvailability;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.FieldError;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.ObserverBinding;
import com.mealmind.gui.logic.SubstituteForm;
import com.mealmind.gui.logic.SubstituteRow;
import com.mealmind.gui.logic.SubstituteRows;
import com.mealmind.gui.logic.SubstituteSession;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.substitution.SubstituteText;
import com.mealmind.substitution.SubstitutionApplier;
import com.mealmind.substitution.SubstitutionProposal;
import com.mealmind.substitution.SubstitutionReason;
import com.mealmind.substitution.SubstitutionRequest;
import com.mealmind.substitution.SubstitutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The Recipes tab's "Substitute an ingredient" section (F07 in the GUI; Stage 1's right-click "Suggest
 * Substitute..." dialog, shown inline). A thin view over the same code as the CLI's {@code recipe substitute}:
 * {@code AgentFacade.suggestSubstitutions} runs in the {@link BackgroundRunner} under the window's shared AI
 * {@link BusyFlag}; Apply goes only through {@link SubstitutionApplier}, which re-checks safety and staleness and
 * runs one undoable command; every text comes from {@link SubstituteText}.
 */
final class SubstitutePane extends TitledPane {

    /** Shown under the controls. */
    static final String HINT = "Suggestions are AI estimates; recipe steps are not rewritten.";
    /** Shown above the suggested options. */
    static final String PENDING_TITLE = "Suggestion (nothing applied yet)";

    private final ApplicationContext ctx;
    private final BackgroundRunner runner;
    private final BiConsumer<String, Boolean> status;
    private final BusyFlag busy;
    private final SubstituteSession session = new SubstituteSession();
    private final List<ObserverBinding> bindings = new ArrayList<>();
    private final ChoiceBox<String> ingredient = new ChoiceBox<>();
    private final ChoiceBox<SubstitutionReason> reason =
            new ChoiceBox<>(FXCollections.observableArrayList(SubstitutionReason.values()));
    private final Button suggest = new Button("Suggest substitutes");
    private final AiUnavailableLabel unavailable;
    private final ProgressIndicator working = new ProgressIndicator();
    private final Label header = new Label();
    private final Label message = new Label();
    private final Label title = new Label(PENDING_TITLE);
    private final Label notes = new Label();
    private final TableView<SubstituteRow> table = new TableView<>();
    private final Button apply = new Button("Apply selected");
    private final Button discard = new Button("Discard");
    private final Map<String, Label> errors = Map.of("recipe", errorLabel(), "ingredient", errorLabel(), "reason", errorLabel());
    private final AgentActivityPanel activity;
    private String recipeId;
    private boolean running;

    SubstitutePane(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status, BusyFlag busy) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.status = Objects.requireNonNull(status, "status");
        this.busy = Objects.requireNonNull(busy, "busy");
        setText("Substitute an ingredient");
        setCollapsible(false);
        unavailable = new AiUnavailableLabel(ctx);
        activity = new AgentActivityPanel(ui);
        activity.log().setPrefRowCount(4);
        show(activity, false);

        reason.setConverter(new StringConverter<>() {
            @Override
            public String toString(SubstitutionReason value) {
                return value == null ? "" : value.getDisplayName();
            }

            @Override
            public SubstitutionReason fromString(String text) {
                return SubstitutionReason.parse(text);
            }
        });
        reason.setValue(SubstitutionReason.PREFERENCE);
        ingredient.setTooltip(new Tooltip("The ingredient to replace"));
        reason.setTooltip(new Tooltip("Why it should be replaced"));
        apply.setTooltip(new Tooltip("Replace the ingredient with the selected option (re-checked for safety; undoable)"));
        discard.setTooltip(new Tooltip("Drop the suggestion"));
        suggest.setOnAction(e -> suggest());
        apply.setOnAction(e -> apply());
        discard.setOnAction(e -> discard());
        working.setPrefSize(18, 18);
        show(working, false);
        Label hint = new Label(HINT);
        hint.getStyleClass().add("hint");
        title.getStyleClass().add("substitute-pending");
        header.setWrapText(true);
        message.setWrapText(true);
        notes.setWrapText(true);

        table.getColumns().add(column("#", SubstituteRow::numberText, 30));
        table.getColumns().add(column("Replacement", SubstituteRow::optionText, 210));
        table.getColumns().add(column("Impact", SubstituteRow::noteText, 200));
        table.getColumns().add(column("Pantry", SubstituteRow::pantryText, 70));
        table.getColumns().add(column("Nutrition", SubstituteRow::nutritionText, 330));
        table.setPlaceholder(new Label("No suggestion yet."));
        table.setPrefHeight(130);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> updateControls());

        Label ingredientLabel = new Label("Ingredient");
        ingredientLabel.setLabelFor(ingredient);
        Label reasonLabel = new Label("Reason");
        reasonLabel.setLabelFor(reason);
        VBox content = new VBox(8, new HBox(8, ingredientLabel, ingredient, reasonLabel, reason, suggest, working, unavailable),
                new HBox(8, errors.get("recipe"), errors.get("ingredient"), errors.get("reason")), hint, message, header,
                title, table, notes, new HBox(8, apply, discard), activity);
        setContent(content);

        bindings.add(ObserverBinding.bind(ctx.recipeBook(), ui, this::refillIngredients));
        bindings.add(ObserverBinding.bind(ctx.llm(), ui, this::refreshAi));
        busy.addListener(this::updateControls);
        clearView();
        refreshAi();
    }

    private static TableColumn<SubstituteRow, String> column(String name, Function<SubstituteRow, String> value, double width) {
        TableColumn<SubstituteRow, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
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

    /**
     * Shows the section for the selected recipe. Choosing another recipe drops the pending suggestion; the same
     * recipe (a refresh) keeps it, and a replaced recipe is caught by the applier's stale check at Apply.
     */
    void showRecipe(String id) {
        if (!Objects.equals(id, recipeId)) {
            recipeId = id;
            session.clear();
            clearView();
            message.setText("");
        }
        refillIngredients();
    }

    /** Refills the ingredient choice from the selected recipe, keeping the chosen ingredient when it still exists. */
    void refillIngredients() {
        String chosen = ingredient.getValue();
        List<String> names = recipeId == null ? List.of()
                : ctx.recipeBook().get(recipeId).map(SubstituteForm::ingredientNames).orElse(List.of());
        ingredient.getItems().setAll(names);
        ingredient.setValue(chosen != null && names.contains(chosen) ? chosen : names.isEmpty() ? null : names.get(0));
        updateControls();
    }

    /** Re-reads AI availability; called on AI service notifications. */
    void refreshAi() {
        String why = AiAvailability.disabledReason(ctx);
        unavailable.update();
        suggest.setTooltip(new Tooltip(why.isEmpty()
                ? "Ask the AI for up to 3 safe substitutes (paid API calls); nothing changes until Apply"
                : AiAvailability.tooltip(why)));
        updateControls();
    }

    private void updateControls() {
        boolean blocked = busy.busy();
        suggest.setDisable(blocked || recipeId == null || !AiAvailability.disabledReason(ctx).isEmpty());
        apply.setDisable(blocked || !session.isPending() || table.getSelectionModel().getSelectedItem() == null);
        discard.setDisable(blocked || !session.isPending());
        ingredient.setDisable(blocked || recipeId == null);
        reason.setDisable(blocked);
        show(working, running);
    }

    private void suggest() {
        errors.values().forEach(label -> label.setText(""));
        FormResult<SubstitutionRequest> parsed = SubstituteForm.parse(recipeId, ingredient.getValue(),
                reason.getValue() == null ? "" : reason.getValue().name());
        if (!parsed.isValid()) {
            for (FieldError error : parsed.errors()) {
                errors.get(error.field()).setText(error.message());
            }
            status.accept("No suggestion requested: " + parsed.errors().get(0).message(), true);
            return;
        }
        SubstitutionRequest request = parsed.value().orElseThrow();
        String forRecipe = recipeId;
        activity.model().clear();
        show(activity, true);
        running = true;
        busy.set(true);
        updateControls();
        runner.submit(() -> ctx.agent().suggestSubstitutions(request, ctx.pantry(), AgentActivityPanel.forwarder(activity.model())),
                result -> {
                    finish();
                    if (!Objects.equals(forRecipe, recipeId)) {
                        status.accept("The selected recipe changed; the suggestion was dropped.", false);
                        return;
                    }
                    showResult(result);
                },
                error -> {
                    // As in the CLI: a failed request keeps any earlier pending suggestion.
                    finish();
                    status.accept(error.getMessage() == null ? String.valueOf(error) : error.getMessage(), true);
                });
    }

    private void finish() {
        running = false;
        busy.set(false);
        show(activity, !activity.model().lines().isEmpty());
        updateControls();
    }

    private void showResult(SubstitutionResult result) {
        SubstitutionProposal proposal = result.proposal().orElse(null);
        if (proposal == null) {
            session.clear();
            clearView();
            message.setText(result.message());
        } else {
            session.set(proposal);
            message.setText("");
            header.setText(SubstituteText.header(proposal));
            show(title, true);
            table.getItems().setAll(SubstituteRows.of(proposal));
            notes.setText(String.join("\n", SubstituteText.noteLines(proposal)));
        }
        status.accept(result.message() + ". " + SubstituteText.statsLine(result), false);
        updateControls();
    }

    private void clearView() {
        header.setText("");
        notes.setText("");
        show(title, false);
        table.getItems().clear();
        updateControls();
    }

    private void apply() {
        SubstitutionProposal proposal = session.proposal().orElse(null);
        int selected = table.getSelectionModel().getSelectedIndex();
        if (proposal == null || selected < 0) {
            return;
        }
        String id = proposal.recipe().getId();
        SubstitutionApplier.ApplyResult result;
        try {
            result = new SubstitutionApplier(ctx.recipeBook(), ctx.validator()).apply(proposal, selected + 1, ctx.history());
        } catch (IllegalStateException e) {
            // As in the CLI: a changed recipe drops the suggestion; a safety refusal keeps it.
            if (session.isStaleIn(ctx.recipeBook())) {
                session.clear();
                clearView();
            }
            status.accept(e.getMessage(), true);
            updateControls();
            return;
        } catch (IllegalArgumentException e) {
            status.accept(e.getMessage(), true);
            return;
        }
        session.clear();
        clearView();
        status.accept(String.join(" ", SubstituteText.appliedLines(result, id, ctx.isBundledRecipe(id))), false);
    }

    private void discard() {
        session.clear();
        clearView();
        message.setText("");
        status.accept(SubstituteText.DISCARDED, false);
    }

    /** Unsubscribes the recipe book and AI bindings. */
    void close() {
        bindings.forEach(ObserverBinding::close);
        bindings.clear();
    }

    ChoiceBox<String> ingredientChoice() {
        return ingredient;
    }

    ChoiceBox<SubstitutionReason> reasonChoice() {
        return reason;
    }

    Button suggestButton() {
        return suggest;
    }

    AiUnavailableLabel unavailableLabel() {
        return unavailable;
    }

    TableView<SubstituteRow> table() {
        return table;
    }

    Button applyButton() {
        return apply;
    }

    Button discardButton() {
        return discard;
    }

    Label messageLabel() {
        return message;
    }

    Label headerLabel() {
        return header;
    }

    Label notesLabel() {
        return notes;
    }

    AgentActivityPanel activity() {
        return activity;
    }

    SubstituteSession session() {
        return session;
    }
}
