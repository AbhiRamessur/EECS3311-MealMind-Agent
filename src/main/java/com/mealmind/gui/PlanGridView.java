package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.PlanCell;
import com.mealmind.gui.logic.PlanDayRow;
import com.mealmind.gui.logic.PlanGrid;
import com.mealmind.gui.logic.PlanGridModel;
import com.mealmind.model.MealType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The Plan tab's calendar (Stage 1 "calendar grid"): one row per day, one column per slot, a day-totals
 * column, then the plan totals and the validator's warnings (safety issues highlighted). Clicking a cell
 * selects it for the slot editor. It always reads the context's <em>current</em> plan.
 */
final class PlanGridView extends VBox {

    private final ApplicationContext ctx;
    private final Runnable onSelect;
    private final GridPane grid = new GridPane();
    private final ToggleGroup group = new ToggleGroup();
    private final Map<String, ToggleButton> cells = new HashMap<>();
    private final Map<String, PlanCell> cellData = new HashMap<>();
    private final Label totals = new Label();
    private final VBox warnings = new VBox(4);
    private Integer selectedDay;
    private MealType selectedSlot;

    PlanGridView(ApplicationContext ctx, Runnable onSelect) {
        super(8);
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
        grid.setHgap(4);
        grid.setVgap(4);
        getChildren().addAll(grid, totals, warnings);
        refresh();
    }

    private static String key(int day, MealType slot) {
        return day + ":" + slot;
    }

    /** Rebuilds the grid from the current plan, keeping the selection when it still exists. */
    void refresh() {
        PlanGrid model = PlanGridModel.of(ctx.plan(), ctx.validator());
        grid.getChildren().clear();
        cells.clear();
        cellData.clear();
        grid.add(new Label("Day"), 0, 0);
        MealType[] slots = MealType.values();
        for (int i = 0; i < slots.length; i++) {
            grid.add(new Label(slots[i].getDisplayName()), i + 1, 0);
        }
        grid.add(new Label("Day totals"), slots.length + 1, 0);
        for (PlanDayRow row : model.rows()) {
            grid.add(new Label(row.dayLabel()), 0, row.day());
            for (PlanCell cell : row.cells()) {
                ToggleButton button = new ToggleButton(cell.text());
                button.setToggleGroup(group);
                button.setWrapText(true);
                button.setPrefWidth(160);
                button.setMaxWidth(Double.MAX_VALUE);
                button.getStyleClass().add(cell.filled() ? "plan-cell-filled" : "plan-cell-empty");
                button.setOnAction(e -> select(cell.day(), cell.slot()));
                grid.add(button, cell.slot().ordinal() + 1, row.day());
                cells.put(key(cell.day(), cell.slot()), button);
                cellData.put(key(cell.day(), cell.slot()), cell);
            }
            grid.add(new Label(row.dayTotalsText()), slots.length + 1, row.day());
        }
        totals.setText(model.totalsText());
        warnings.getChildren().clear();
        model.safetyIssues().forEach(issue -> warnings.getChildren().add(styled("SAFETY: " + issue, "banner-warning")));
        model.warnings().forEach(warning -> warnings.getChildren().add(styled("Warning: " + warning, "hint")));
        if (selectedDay != null && cells.containsKey(key(selectedDay, selectedSlot))) {
            cells.get(key(selectedDay, selectedSlot)).setSelected(true);
        } else {
            selectedDay = null;
            selectedSlot = null;
        }
    }

    private static Label styled(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** Selects a cell (also used by the self-test). */
    void select(int day, MealType slot) {
        selectedDay = day;
        selectedSlot = slot;
        ToggleButton button = cells.get(key(day, slot));
        if (button != null) {
            button.setSelected(true);
        }
        onSelect.run();
    }

    /** The selected cell as currently shown, if any. */
    Optional<PlanCell> selected() {
        return selectedDay == null ? Optional.empty() : Optional.ofNullable(cellData.get(key(selectedDay, selectedSlot)));
    }

    ToggleButton cell(int day, MealType slot) {
        return cells.get(key(day, slot));
    }

    int rowCount() {
        return (int) cellData.values().stream().map(PlanCell::day).distinct().count();
    }

    long filledCount() {
        return cellData.values().stream().filter(PlanCell::filled).count();
    }

    Label totalsLabel() {
        return totals;
    }
}
