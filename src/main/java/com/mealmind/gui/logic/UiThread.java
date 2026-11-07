package com.mealmind.gui.logic;

/**
 * Runs work on the user-interface thread. Model observers fire on whatever thread changed the model, so
 * every GUI callback goes through this interface; the JavaFX implementation posts to the FX thread.
 */
@FunctionalInterface
public interface UiThread {

    /**
     * Runs {@code task} on the UI thread (now, or later if called from another thread).
     *
     * @param task the work to run
     */
    void run(Runnable task);
}
