package com.mealmind.command;

/**
 * Shared executed/not-executed bookkeeping for the concrete commands, so each one only implements
 * the actual change ({@link #doExecute()}) and its reversal ({@link #doUndo()}).
 */
abstract class AbstractCommand implements Command {

    private boolean executed;

    @Override
    public final void execute() {
        if (executed) {
            throw new IllegalStateException("Already executed: " + description());
        }
        doExecute();
        // Only reached if doExecute() did not throw, so a failed command stays not-executed.
        executed = true;
    }

    @Override
    public final void undo() {
        if (!executed) {
            throw new IllegalStateException("Not executed, nothing to undo: " + description());
        }
        doUndo();
        executed = false;
    }

    /** Applies the change; must leave the receiver unchanged if it throws. */
    protected abstract void doExecute();

    /** Reverses the change made by the last {@link #doExecute()}. */
    protected abstract void doUndo();
}
