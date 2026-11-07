package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.AiStatusModel;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.ObserverBinding;
import com.mealmind.gui.logic.StatusMessages;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.gui.logic.UndoRedoModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The main window's controller (Stage 1 {@code MainController}): a toolbar with Undo/Redo and the AI
 * status, a tab pane with the Profile, Pantry and Recipes tabs, and a status bar. Every view is subscribed to its
 * model through an {@link ObserverBinding} on the FX thread; {@link #close()} unsubscribes them all.
 *
 * <p>It works on the same {@link ApplicationContext} as the CLI: the same models, commands and history.
 */
public final class MainController implements AutoCloseable {

    private final List<ObserverBinding> bindings = new ArrayList<>();
    private final BackgroundRunner background;
    private final StatusMessages messages = new StatusMessages();
    private final Label statusBar = new Label();
    private final Label aiStatus = new Label();
    private final UndoRedoToolBar toolBar;
    private final ProfileTab profileTab;
    private final PantryTab pantryTab;
    private final RecipesTab recipesTab;
    private final BorderPane root = new BorderPane();

    /**
     * Builds the window content. Must be called on the FX application thread.
     *
     * @param ctx        the session shared with the CLI
     * @param configNote a startup note for the status bar (e.g. why AI is disabled), or null
     * @throws NullPointerException if {@code ctx} is null
     */
    public MainController(ApplicationContext ctx, String configNote) {
        Objects.requireNonNull(ctx, "ctx");
        UiThread ui = new FxUiThread();
        background = new BackgroundRunner(ui);

        toolBar = new UndoRedoToolBar(new UndoRedoModel(ctx.history()), this::showStatus);
        bindings.add(ObserverBinding.bind(ctx.history(), ui, toolBar::refresh));
        AiStatusModel ai = new AiStatusModel(ctx.llm());
        aiStatus.getStyleClass().add("ai-status");
        aiStatus.setText(ai.text());
        bindings.add(ObserverBinding.bind(ctx.llm(), ui, () -> aiStatus.setText(ai.text())));

        profileTab = new ProfileTab(ctx.profile(), this::showStatus);
        bindings.add(ObserverBinding.bind(ctx.profile(), ui, profileTab::onProfileChanged));
        pantryTab = new PantryTab(ctx.pantry(), ctx.history(), () -> LocalDate.now(ctx.clock()), this::showStatus);
        bindings.add(ObserverBinding.bind(ctx.pantry(), ui, pantryTab::refresh));
        recipesTab = new RecipesTab(ctx, background, ui, this::showStatus);
        bindings.add(ObserverBinding.bind(ctx.recipeBook(), ui, recipesTab::refresh));
        bindings.add(ObserverBinding.bind(ctx.preferences(), ui, recipesTab::refresh));
        // Safety flags depend on the profile.
        bindings.add(ObserverBinding.bind(ctx.profile(), ui, recipesTab::refresh));
        bindings.add(ObserverBinding.bind(ctx.agent(), ui, recipesTab::refreshPending));
        bindings.add(ObserverBinding.bind(ctx.llm(), ui, recipesTab::refreshAi));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, toolBar, spacer, aiStatus);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("top-bar");
        TabPane tabs = new TabPane(profileTab, pantryTab, recipesTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        root.setTop(top);
        root.setCenter(tabs);
        root.setBottom(statusBar);
        showStatus(configNote == null || configNote.isBlank() ? "Ready." : configNote, false);
    }

    /**
     * Returns the window's root node.
     *
     * @return the root pane
     */
    public BorderPane root() {
        return root;
    }

    /**
     * Installs the window-wide keyboard shortcuts (Ctrl+Z undo, Ctrl+Y redo).
     *
     * @param scene the window's scene
     */
    public void installAccelerators(Scene scene) {
        toolBar.installAccelerators(scene);
    }

    /**
     * Shows a message in the status bar.
     *
     * @param message the text
     * @param error   {@code true} to style it as an error
     */
    public void showStatus(String message, boolean error) {
        if (error) {
            messages.error(message);
        } else {
            messages.info(message);
        }
        statusBar.setText(messages.message());
        statusBar.getStyleClass().removeAll("status-error", "status-info");
        statusBar.getStyleClass().add(error ? "status-error" : "status-info");
    }

    /**
     * Returns the background runner for slow jobs (used by the AI features in later commits).
     *
     * @return the runner
     */
    public BackgroundRunner background() {
        return background;
    }

    /** Unsubscribes every view from its model and stops the background runner. */
    @Override
    public void close() {
        bindings.forEach(ObserverBinding::close);
        bindings.clear();
        background.close();
    }

    UndoRedoToolBar toolBar() {
        return toolBar;
    }

    ProfileTab profileTab() {
        return profileTab;
    }

    PantryTab pantryTab() {
        return pantryTab;
    }

    RecipesTab recipesTab() {
        return recipesTab;
    }

    Label aiStatusLabel() {
        return aiStatus;
    }

    Label statusBar() {
        return statusBar;
    }
}
