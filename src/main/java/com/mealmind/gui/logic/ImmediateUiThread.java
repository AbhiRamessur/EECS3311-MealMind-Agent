package com.mealmind.gui.logic;

/**
 * A {@link UiThread} that runs each task at once on the calling thread; for tests and the GUI self-test.
 */
public final class ImmediateUiThread implements UiThread {

    @Override
    public void run(Runnable task) {
        task.run();
    }
}
