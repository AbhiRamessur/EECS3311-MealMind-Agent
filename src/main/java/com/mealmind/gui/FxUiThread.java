package com.mealmind.gui;

import com.mealmind.gui.logic.UiThread;
import javafx.application.Platform;

/**
 * The JavaFX {@link UiThread}: runs a task directly when already on the FX application thread, otherwise
 * posts it with {@link Platform#runLater}.
 */
public final class FxUiThread implements UiThread {

    @Override
    public void run(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }
}
