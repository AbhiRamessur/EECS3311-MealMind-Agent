package com.mealmind.command;

import com.mealmind.model.ObservableSubject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * The Invoker of the Command pattern: runs {@link Command}s and keeps undo and redo stacks.
 *
 * <p>{@link #pushAndExecute} is the only way to add a command, and it executes the command
 * <em>first</em>, recording it only if that succeeds. (Stage 1 listed a separate {@code push()} in
 * one traceability row; it was left out because pushing without executing would let the history and
 * the model state disagree.) A new command clears the redo stack. The undo stack holds at most
 * {@link #getMaxSize()} commands; beyond that the oldest one is dropped.
 *
 * <p>A ConcreteSubject in the Observer pattern: observers are notified once after each successful
 * {@code pushAndExecute}, {@code undo}, {@code redo} and {@code clear}, so an undo button can
 * enable or disable itself. A failed operation leaves the history unchanged and notifies no one.
 */
public final class CommandHistory extends ObservableSubject {

    /** Default maximum number of undoable commands. */
    public static final int DEFAULT_MAX_SIZE = 100;

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final int maxSize;

    /** Creates a history holding up to {@value #DEFAULT_MAX_SIZE} undoable commands. */
    public CommandHistory() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a history with a custom limit.
     *
     * @param maxSize the maximum number of undoable commands, {@code >= 1}
     * @throws IllegalArgumentException if {@code maxSize < 1}
     */
    public CommandHistory(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("History size must be at least 1, got " + maxSize);
        }
        this.maxSize = maxSize;
    }

    /**
     * Executes a command and, if that succeeds, records it for undo and clears the redo stack.
     *
     * @param command the command to run
     * @throws NullPointerException if {@code command} is null
     * @throws RuntimeException     whatever {@code command.execute()} throws; the history is then unchanged
     */
    public void pushAndExecute(Command command) {
        Objects.requireNonNull(command, "command");
        command.execute();
        pushUndo(command);
        redoStack.clear();
        notifyObservers("Executed: " + command.description());
    }

    /**
     * Undoes the most recent command.
     *
     * @return {@code false} if there was nothing to undo, otherwise {@code true}
     * @throws RuntimeException whatever the command's {@code undo()} throws; it then stays on the undo stack
     */
    public boolean undo() {
        Command command = undoStack.peek();
        if (command == null) {
            return false;
        }
        command.undo();
        undoStack.pop();
        redoStack.push(command);
        notifyObservers("Undone: " + command.description());
        return true;
    }

    /**
     * Re-executes the most recently undone command.
     *
     * @return {@code false} if there was nothing to redo, otherwise {@code true}
     * @throws RuntimeException whatever the command's {@code execute()} throws; it then stays on the redo stack
     */
    public boolean redo() {
        Command command = redoStack.peek();
        if (command == null) {
            return false;
        }
        command.execute();
        redoStack.pop();
        pushUndo(command);
        notifyObservers("Redone: " + command.description());
        return true;
    }

    private void pushUndo(Command command) {
        undoStack.push(command);
        if (undoStack.size() > maxSize) {
            undoStack.removeLast();
        }
    }

    /**
     * Returns whether there is a command to undo.
     *
     * @return {@code true} if {@link #undo()} would do something
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Returns whether there is a command to redo.
     *
     * @return {@code true} if {@link #redo()} would do something
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Describes the command {@link #undo()} would reverse, e.g. for an "Undo ..." menu item.
     *
     * @return its description, or empty if there is nothing to undo
     */
    public Optional<String> nextUndoDescription() {
        return Optional.ofNullable(undoStack.peek()).map(Command::description);
    }

    /**
     * Describes the command {@link #redo()} would re-apply.
     *
     * @return its description, or empty if there is nothing to redo
     */
    public Optional<String> nextRedoDescription() {
        return Optional.ofNullable(redoStack.peek()).map(Command::description);
    }

    /**
     * Returns the number of undoable commands.
     *
     * @return the undo stack size, at most {@link #getMaxSize()}
     */
    public int undoSize() {
        return undoStack.size();
    }

    /**
     * Returns the number of redoable commands.
     *
     * @return the redo stack size
     */
    public int redoSize() {
        return redoStack.size();
    }

    /**
     * Returns the maximum number of undoable commands.
     *
     * @return the limit
     */
    public int getMaxSize() {
        return maxSize;
    }

    /** Forgets all undo and redo entries; notifies observers only if there were any. */
    public void clear() {
        if (undoStack.isEmpty() && redoStack.isEmpty()) {
            return;
        }
        undoStack.clear();
        redoStack.clear();
        notifyObservers("History cleared");
    }
}
