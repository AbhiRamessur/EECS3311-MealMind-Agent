package com.mealmind.gui;

import com.mealmind.agent.PlanMeals;
import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
import com.mealmind.command.ClearMealCommand;
import com.mealmind.command.ReplaceMealCommand;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.PlanAssignForm;
import com.mealmind.gui.logic.PlanAssignment;
import com.mealmind.gui.logic.PlanCell;
import com.mealmind.model.Meal;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeScaler;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The Plan tab's "Edit selected slot" controls: the GUI's {@code plan set} and {@code plan clear}. A meal is
 * built with {@link PlanMeals} and checked for safety exactly like {@code plan set}: an unsafe recipe is
 * refused and nothing runs. Changes go through the command history, so Undo reverts them.
 */
final class PlanSlotEditor extends VBox {

    private final ApplicationContext ctx;
    private final BiConsumer<String, Boolean> status;
    private final Supplier<Optional<PlanCell>> selected;
    private final Label selection = new Label("Select a slot in the plan");
    private final ChoiceBox<String> recipe = new ChoiceBox<>();
    private final TextField servings = new TextField();
    private final Button setMeal = new Button("Set meal");
    private final Button clearSlot = new Button("Clear slot");
    private final Label result = new Label();

    PlanSlotEditor(ApplicationContext ctx, BiConsumer<String, Boolean> status, Supplier<Optional<PlanCell>> selected) {
        super(6);
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.status = Objects.requireNonNull(status, "status");
        this.selected = Objects.requireNonNull(selected, "selected");
        recipe.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                return id == null ? "" : id + " " + ctx.recipeBook().get(id).map(Recipe::getName).orElse("");
            }

            @Override
            public String fromString(String text) {
                return text == null ? null : text.split(" ", 2)[0];
            }
        });
        servings.setPromptText("recipe default");
        servings.setPrefColumnCount(6);
        setMeal.setTooltip(new Tooltip("Put the chosen recipe in the selected slot (undoable)"));
        clearSlot.setTooltip(new Tooltip("Empty the selected slot (undoable)"));
        setMeal.setOnAction(e -> setMeal());
        clearSlot.setOnAction(e -> clearSlot());
        result.setWrapText(true);
        Label recipeLabel = new Label("Recipe");
        recipeLabel.setLabelFor(recipe);
        Label servingsLabel = new Label("Servings");
        servingsLabel.setLabelFor(servings);
        Label title = new Label("Edit selected slot");
        title.getStyleClass().add("detail-title");
        getChildren().addAll(title, selection, new HBox(6, recipeLabel, recipe), new HBox(6, servingsLabel, servings),
                new HBox(6, setMeal, clearSlot), result);
        refreshRecipes();
        refresh();
    }

    /** Reloads the recipe choices from the recipe book. */
    void refreshRecipes() {
        String chosen = recipe.getValue();
        recipe.getItems().setAll(ctx.recipeBook().getAll().stream().map(Recipe::getId).toList());
        if (chosen != null && recipe.getItems().contains(chosen)) {
            recipe.setValue(chosen);
        }
    }

    /** Updates the selection text and button states. */
    void refresh() {
        Optional<PlanCell> cell = selected.get();
        selection.setText(cell.map(c -> "Day " + c.day() + " " + c.slot().getDisplayName() + ": " + c.text())
                .orElse("Select a slot in the plan"));
        setMeal.setDisable(cell.isEmpty());
        clearSlot.setDisable(cell.isEmpty() || !cell.get().filled());
    }

    private void setMeal() {
        Optional<PlanCell> cell = selected.get();
        FormResult<PlanAssignment> parsed = PlanAssignForm.parse(cell.map(PlanCell::day).orElse(null),
                cell.map(PlanCell::slot).orElse(null), recipe.getValue(), servings.getText(), ctx.plan().getNumberOfDays());
        if (!parsed.isValid()) {
            show(List.of("Not set: " + parsed.errors().get(0).message()), true);
            return;
        }
        PlanAssignment assignment = parsed.value().orElseThrow();
        Optional<Recipe> found = ctx.recipeBook().get(assignment.recipeId());
        if (found.isEmpty()) {
            show(List.of("No recipe with id '" + assignment.recipeId() + "'"), true);
            return;
        }
        Recipe chosen = assignment.servings() == null ? found.get() : RecipeScaler.scale(found.get(), assignment.servings());
        Meal meal = PlanMeals.forRecipe(assignment.slot(), chosen);
        // The plan set rule: a safety violation aborts the edit and leaves the plan untouched.
        List<Violation> unsafe = ctx.validator().validateAgainstBlacklists(meal).getSafetyViolations();
        if (!unsafe.isEmpty()) {
            show(unsafe.stream().map(v -> "Refused: " + v.message()).toList(), true);
            return;
        }
        ctx.history().pushAndExecute(new ReplaceMealCommand(ctx.plan(), assignment.day(), assignment.slot(), meal));
        List<String> lines = new ArrayList<>();
        lines.add("Day " + assignment.day() + " " + assignment.slot().getDisplayName() + ": " + CLIHandler.describe(meal));
        ctx.validator().validateMacrosAndAllergens(ctx.plan()).getAdvisories().forEach(v -> lines.add("Warning: " + v.message()));
        show(lines, false);
    }

    private void clearSlot() {
        Optional<PlanCell> cell = selected.get();
        if (cell.isEmpty()) {
            return;
        }
        try {
            ctx.history().pushAndExecute(new ClearMealCommand(ctx.plan(), cell.get().day(), cell.get().slot()));
            show(List.of("Cleared Day " + cell.get().day() + " " + cell.get().slot().getDisplayName() + "."), false);
        } catch (RuntimeException e) {
            show(List.of(e.getMessage()), true);
        }
    }

    private void show(List<String> lines, boolean error) {
        result.setText(String.join("\n", lines));
        status.accept(lines.get(0), error);
    }

    ChoiceBox<String> recipeChoice() {
        return recipe;
    }

    TextField servingsField() {
        return servings;
    }

    Button setMealButton() {
        return setMeal;
    }

    Button clearSlotButton() {
        return clearSlot;
    }

    Label resultLabel() {
        return result;
    }
}
