package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The JavaFX application. {@link Application#launch} creates this class itself and cannot pass objects
 * to it, so the session is handed over first with {@link #configure}; {@code Main --gui} does both.
 */
public final class MealMindApp extends Application {

    private static volatile ApplicationContext context;
    private static volatile String note;
    private MainController controller;

    /**
     * Hands over the session before {@link Application#launch}.
     *
     * @param ctx        the session (the same one the CLI would use)
     * @param configNote a startup note for the status bar, or null
     * @throws NullPointerException if {@code ctx} is null
     */
    public static void configure(ApplicationContext ctx, String configNote) {
        context = Objects.requireNonNull(ctx, "ctx");
        note = configNote;
    }

    /**
     * Builds and shows the main window.
     *
     * @param stage the primary stage
     * @throws IllegalStateException if {@link #configure} was not called
     */
    @Override
    public void start(Stage stage) {
        ApplicationContext ctx = context;
        if (ctx == null) {
            throw new IllegalStateException("MealMindApp.configure must be called before launch");
        }
        controller = new MainController(ctx, note);
        Scene scene = new Scene(controller.root(), 1000, 680);
        scene.getStylesheets().add(Objects.requireNonNull(MealMindApp.class.getResource("/gui/mealmind.css")).toExternalForm());
        controller.installAccelerators(scene);
        stage.setTitle("MealMind");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    /** Unsubscribes the views when the window closes. */
    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }
}
