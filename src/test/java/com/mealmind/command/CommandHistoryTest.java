package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.ChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandHistoryTest {

    /** Test command that records execute/undo calls in a shared log and can be told to fail. */
    private static final class RecordingCommand implements Command {
        private final String name;
        private final List<String> log;
        boolean failExecute;
        boolean failUndo;

        RecordingCommand(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public void execute() {
            if (failExecute) {
                throw new IllegalStateException("execute failed: " + name);
            }
            log.add("do " + name);
        }

        @Override
        public void undo() {
            if (failUndo) {
                throw new IllegalStateException("undo failed: " + name);
            }
            log.add("undo " + name);
        }

        @Override
        public String description() {
            return name;
        }
    }

    private List<String> log;
    private List<ChangeEvent> events;
    private CommandHistory history;

    @BeforeEach
    void setUp() {
        log = new ArrayList<>();
        events = new ArrayList<>();
        history = new CommandHistory();
        history.addObserver(events::add);
    }

    private RecordingCommand command(String name) {
        return new RecordingCommand(name, log);
    }

    @Test
    void pushAndExecuteRunsCommandAndEnablesUndo() {
        history.pushAndExecute(command("a"));

        assertEquals(List.of("do a"), log);
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(1, history.undoSize());
    }

    @Test
    void undoThenRedo() {
        history.pushAndExecute(command("a"));

        assertTrue(history.undo());
        assertFalse(history.canUndo());
        assertTrue(history.canRedo());
        assertTrue(history.redo());

        assertEquals(List.of("do a", "undo a", "do a"), log);
        assertEquals(1, history.undoSize());
        assertEquals(0, history.redoSize());
    }

    @Test
    void newPushAfterUndoClearsRedoStack() {
        history.pushAndExecute(command("a"));
        history.undo();
        history.pushAndExecute(command("b"));

        assertFalse(history.canRedo());
        assertEquals(Optional.of("b"), history.nextUndoDescription());
    }

    @Test
    void undoAndRedoOnEmptyHistoryReturnFalse() {
        assertFalse(history.undo());
        assertFalse(history.redo());
        assertEquals(0, events.size());
    }

    @Test
    void failingExecuteIsNotRecordedAndDoesNotNotify() {
        history.pushAndExecute(command("a"));
        history.undo();
        events.clear();
        RecordingCommand failing = command("bad");
        failing.failExecute = true;

        assertThrows(IllegalStateException.class, () -> history.pushAndExecute(failing));

        assertEquals(0, history.undoSize());
        assertEquals(1, history.redoSize());
        assertEquals(Optional.of("a"), history.nextRedoDescription());
        assertEquals(0, events.size());
    }

    @Test
    void failingUndoKeepsCommandOnUndoStack() {
        RecordingCommand failing = command("a");
        history.pushAndExecute(failing);
        failing.failUndo = true;
        events.clear();

        assertThrows(IllegalStateException.class, () -> history.undo());

        assertEquals(1, history.undoSize());
        assertEquals(0, history.redoSize());
        assertEquals(0, events.size());
    }

    @Test
    void maxSizeDropsOldestEntry() {
        CommandHistory small = new CommandHistory(2);
        small.pushAndExecute(command("a"));
        small.pushAndExecute(command("b"));
        small.pushAndExecute(command("c"));

        assertEquals(2, small.undoSize());
        small.undo();
        small.undo();
        assertFalse(small.undo());
        assertEquals(List.of("do a", "do b", "do c", "undo c", "undo b"), log);
    }

    @Test
    void reportsNextUndoAndRedoDescriptions() {
        assertEquals(Optional.empty(), history.nextUndoDescription());
        history.pushAndExecute(command("a"));
        history.pushAndExecute(command("b"));
        history.undo();

        assertEquals(Optional.of("a"), history.nextUndoDescription());
        assertEquals(Optional.of("b"), history.nextRedoDescription());
    }

    @Test
    void clearEmptiesBothStacks() {
        history.pushAndExecute(command("a"));
        history.pushAndExecute(command("b"));
        history.undo();
        events.clear();

        history.clear();
        history.clear();

        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(1, events.size());
    }

    @Test
    void notifiesOncePerSuccessfulOperation() {
        RecordingCommand a = command("a");
        history.pushAndExecute(a);
        history.undo();
        history.redo();
        history.clear();

        assertEquals(4, events.size());
        assertSame(history, events.get(0).source());
        assertEquals("Executed: a", events.get(0).description());
        assertEquals("Undone: a", events.get(1).description());
        assertEquals("Redone: a", events.get(2).description());
    }

    @Test
    void rejectsInvalidMaxSizeAndNullCommand() {
        assertThrows(IllegalArgumentException.class, () -> new CommandHistory(0));
        assertThrows(NullPointerException.class, () -> history.pushAndExecute(null));
        assertEquals(CommandHistory.DEFAULT_MAX_SIZE, history.getMaxSize());
    }
}
