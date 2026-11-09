package com.mealmind.command;

import java.util.List;
import java.util.Objects;

/**
 * A macro command: several {@link Command}s run as one undoable step (Command pattern; a composite of
 * commands). Used by F10 so that a whole leftover proposal is added, undone and redone in one step.
 *
 * <p>Execution runs the children in order. If a child throws, the children that already ran are undone
 * in reverse order and the original exception is rethrown, so the receivers are unchanged and the macro
 * stays not-executed (the {@link Command} contract). An exception thrown while rolling back is added to
 * the original as a suppressed exception. Undo runs the children in reverse order.
 */
public final class MacroCommand extends AbstractCommand {

    private final String description;
    private final List<Command> children;

    /**
     * Creates the macro command.
     *
     * @param description the text returned by {@link #description()}, e.g. "Add 3 leftover meal(s) to the plan"
     * @param children    the commands to run, in order; copied
     * @throws NullPointerException     if {@code description}, {@code children} or a child is null
     * @throws IllegalArgumentException if {@code children} is empty
     */
    public MacroCommand(String description, List<Command> children) {
        this.description = Objects.requireNonNull(description, "description");
        this.children = List.copyOf(Objects.requireNonNull(children, "children"));
        if (this.children.isEmpty()) {
            throw new IllegalArgumentException("A macro command needs at least one command");
        }
    }

    @Override
    protected void doExecute() {
        int executed = 0;
        try {
            for (Command child : children) {
                child.execute();
                executed++;
            }
        } catch (RuntimeException failure) {
            for (int i = executed - 1; i >= 0; i--) {
                try {
                    children.get(i).undo();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    protected void doUndo() {
        for (int i = children.size() - 1; i >= 0; i--) {
            children.get(i).undo();
        }
    }

    /**
     * Returns the number of child commands.
     *
     * @return at least 1
     */
    public int size() {
        return children.size();
    }

    @Override
    public String description() {
        return description;
    }
}
