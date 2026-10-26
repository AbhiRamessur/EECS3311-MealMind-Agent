package com.mealmind.cli;

import com.mealmind.model.ChangeEvent;
import com.mealmind.model.ModelObserver;
import java.io.PrintStream;
import java.util.Objects;

/**
 * The CLI's ConcreteObserver from the Stage 1 Observer design: prints one line whenever the subject
 * it watches changes. {@link CLIHandler} subscribes it to the shopping-list generator, which already
 * reacts to every pantry and plan change, so each change is reported once.
 */
public final class CLIConsoleView implements ModelObserver {

    private final PrintStream out;

    /**
     * Creates a view writing to {@code out}.
     *
     * @param out where update lines are printed
     * @throws NullPointerException if {@code out} is null
     */
    public CLIConsoleView(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    /**
     * Prints {@code "[update] <description>"}.
     *
     * @param event the change
     */
    @Override
    public void onStateChanged(ChangeEvent event) {
        out.println("[update] " + event.description());
    }
}
