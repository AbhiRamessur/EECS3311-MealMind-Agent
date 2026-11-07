package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.UiThread;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;

/**
 * The Plan tab (F05 rule-based generation, manual edits, F06 AI edits): the generate bar on top, the calendar
 * grid in the centre, the slot editor on the right and the "Ask the planner" bar at the bottom. It always
 * reads the context's current plan; {@code onReplaced} re-points the plan observers after a replacement.
 */
final class PlanTab extends Tab {

    private final PlanGenerateBar generateBar;
    private final PlanGridView grid;
    private final PlanSlotEditor editor;
    private final PlanChatBar chat;
    /**
     * Asks before replacing a plan with meals. Production shows a standard confirmation Alert; the self-test
     * and the GUI tests replace it through {@link #confirmReplacementsWith} so no dialog blocks them.
     */
    private Predicate<String> confirmer = PlanTab::confirmWithAlert;

    PlanTab(ApplicationContext ctx, BackgroundRunner runner, UiThread ui, BiConsumer<String, Boolean> status, BusyFlag busy,
            Runnable onReplaced) {
        super("Plan");
        Objects.requireNonNull(ctx, "ctx");
        setClosable(false);
        grid = new PlanGridView(ctx, this::onSelect);
        editor = new PlanSlotEditor(ctx, status, grid::selected);
        generateBar = new PlanGenerateBar(ctx, status, message -> confirmer.test(message), onReplaced);
        chat = new PlanChatBar(ctx, runner, ui, status, busy);
        BorderPane content = new BorderPane();
        content.setTop(generateBar);
        ScrollPane gridScroll = new ScrollPane(grid);
        gridScroll.setFitToWidth(true);
        content.setCenter(gridScroll);
        content.setRight(editor);
        content.setBottom(chat);
        content.getStyleClass().add("tab-content");
        editor.setPrefWidth(300);
        setContent(content);
    }

    private static boolean confirmWithAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void onSelect() {
        editor.refresh();
    }

    /** Re-reads the current plan; called on plan and profile notifications and after a replacement. */
    void refresh() {
        grid.refresh();
        editor.refresh();
    }

    /** Re-reads the recipe choices; called on recipe book notifications. */
    void refreshRecipes() {
        editor.refreshRecipes();
    }

    /** Re-reads AI availability; called on AI service notifications. */
    void refreshAi() {
        chat.refreshAi();
    }

    /** Replaces the confirmation dialog (self-test and GUI tests only; production keeps the Alert). */
    void confirmReplacementsWith(Predicate<String> answer) {
        confirmer = Objects.requireNonNull(answer, "answer");
    }

    PlanGenerateBar generateBar() {
        return generateBar;
    }

    PlanGridView grid() {
        return grid;
    }

    PlanSlotEditor editor() {
        return editor;
    }

    PlanChatBar chat() {
        return chat;
    }
}
