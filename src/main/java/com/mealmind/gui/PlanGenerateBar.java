package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.FieldError;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.PlanGenerateForm;
import com.mealmind.gui.logic.StrategyOption;
import com.mealmind.model.DayPlan;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.planning.PlanRequest;
import com.mealmind.planning.PlanResult;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The Plan tab's generate bar (F05, rule-based): days, strategy, slots and budget, then "Generate
 * (rule-based)", plus "New plan". Both replace the whole plan exactly like the CLI (generate first, then
 * {@code ctx.newPlan} and copy the meals), after a confirmation when the current plan has meals. Replacing a
 * plan clears the undo history, so it is not undoable.
 */
final class PlanGenerateBar extends VBox {

    private final ApplicationContext ctx;
    private final BiConsumer<String, Boolean> status;
    private final Predicate<String> confirmer;
    private final Runnable onReplaced;
    private final TextField days = new TextField("7");
    private final ChoiceBox<StrategyOption> strategy = new ChoiceBox<>(FXCollections.observableArrayList(PlanGenerateForm.strategyOptions()));
    private final Map<MealType, CheckBox> slots = new EnumMap<>(MealType.class);
    private final TextField budget = new TextField();
    private final Button generate = new Button("Generate (rule-based)");
    private final TextField newDays = new TextField("7");
    private final Button newPlan = new Button("New plan");
    private final Label errors = new Label();
    private final Label output = new Label();

    PlanGenerateBar(ApplicationContext ctx, BiConsumer<String, Boolean> status, Predicate<String> confirmer, Runnable onReplaced) {
        super(6);
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.status = Objects.requireNonNull(status, "status");
        this.confirmer = Objects.requireNonNull(confirmer, "confirmer");
        this.onReplaced = Objects.requireNonNull(onReplaced, "onReplaced");
        strategy.setConverter(new StringConverter<>() {
            @Override
            public String toString(StrategyOption option) {
                return option == null ? "" : option.displayName();
            }

            @Override
            public StrategyOption fromString(String text) {
                return strategy.getItems().stream().filter(o -> o.displayName().equals(text)).findFirst().orElse(null);
            }
        });
        strategy.setValue(strategy.getItems().get(0));
        for (MealType type : MealType.values()) {
            CheckBox box = new CheckBox(type.getDisplayName());
            box.setSelected(type == MealType.LUNCH || type == MealType.DINNER);
            slots.put(type, box);
        }
        days.setPrefColumnCount(3);
        newDays.setPrefColumnCount(3);
        budget.setPromptText("none");
        budget.setPrefColumnCount(6);
        generate.setTooltip(new Tooltip("Fill the plan from the recipe book without AI (replaces the plan; not undoable)"));
        newPlan.setTooltip(new Tooltip("Start an empty plan (not undoable)"));
        generate.setOnAction(e -> generate());
        newPlan.setOnAction(e -> newPlan());
        errors.getStyleClass().add("field-error");
        errors.setWrapText(true);
        output.setWrapText(true);
        Label hint = new Label("Replacing the whole plan cannot be undone; later edits can.");
        hint.getStyleClass().add("hint");

        FlowPane row = new FlowPane(8, 6, labelled("Days", days), labelled("Strategy", strategy));
        slots.values().forEach(row.getChildren()::add);
        row.getChildren().addAll(labelled("Budget ($)", budget), generate, labelled("New plan days", newDays), newPlan);
        getChildren().addAll(row, errors, hint, output);
    }

    private static VBox labelled(String text, Control control) {
        Label label = new Label(text);
        label.setLabelFor(control);
        return new VBox(2, label, control);
    }

    private void generate() {
        errors.setText("");
        Set<MealType> chosen = EnumSet.noneOf(MealType.class);
        slots.forEach((type, box) -> {
            if (box.isSelected()) {
                chosen.add(type);
            }
        });
        FormResult<PlanRequest> parsed = PlanGenerateForm.parse(days.getText(),
                strategy.getValue() == null ? "" : strategy.getValue().id(), chosen, budget.getText());
        if (!parsed.isValid()) {
            showErrors(parsed.errors());
            return;
        }
        if (!confirmReplace()) {
            return;
        }
        PlanRequest request = parsed.value().orElseThrow();
        PlanResult result;
        try {
            // Generated first, so a failure leaves the current plan untouched (as in the CLI).
            result = ctx.planner().generate(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            status.accept(e.getMessage(), true);
            return;
        }
        MealPlan current = ctx.newPlan(request.days());
        for (DayPlan day : result.plan().getDays()) {
            for (MealType type : day.getFilledSlots()) {
                current.setMeal(day.getDayNumber(), type, day.getMeal(type).orElseThrow());
            }
        }
        onReplaced.run();
        List<String> lines = new ArrayList<>();
        lines.add("Generated " + request.days() + "-day plan using " + request.strategy().displayName() + " (rule-based, no AI)");
        lines.addAll(result.notes());
        result.validation().getAdvisories().forEach(v -> lines.add("Warning: " + v.message()));
        result.validation().getSafetyViolations().stream().map(Violation::message).forEach(m -> lines.add("SAFETY: " + m));
        output.setText(String.join("\n", lines));
        status.accept(lines.get(0), false);
    }

    private void newPlan() {
        errors.setText("");
        FormResult<Integer> parsed = PlanGenerateForm.parseDays(newDays.getText());
        if (!parsed.isValid()) {
            showErrors(parsed.errors());
            return;
        }
        if (!confirmReplace()) {
            return;
        }
        MealPlan plan = ctx.newPlan(parsed.value().orElseThrow());
        onReplaced.run();
        output.setText("");
        status.accept("Started a new " + plan.getNumberOfDays() + "-day plan. Undo history cleared.", false);
    }

    /** Asks before replacing a plan that has meals (the CLI's --replace rule). */
    private boolean confirmReplace() {
        boolean hasMeals = ctx.plan().getDays().stream().anyMatch(day -> !day.getFilledSlots().isEmpty());
        return !hasMeals || confirmer.test("The current plan already has meals. Replace it? This cannot be undone.");
    }

    private void showErrors(List<FieldError> problems) {
        errors.setText(problems.stream().map(e -> e.field() + ": " + e.message()).collect(Collectors.joining("\n")));
        status.accept("Not generated: " + problems.get(0).field() + " " + problems.get(0).message(), true);
    }

    TextField daysField() {
        return days;
    }

    ChoiceBox<StrategyOption> strategyChoice() {
        return strategy;
    }

    CheckBox slotBox(MealType type) {
        return slots.get(type);
    }

    TextField budgetField() {
        return budget;
    }

    Button generateButton() {
        return generate;
    }

    Button newPlanButton() {
        return newPlan;
    }

    TextField newDaysField() {
        return newDays;
    }

    Label outputLabel() {
        return output;
    }
}
