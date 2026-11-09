package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MacroCommandTest {

    /** Records calls in a shared log; can be told to fail on execute or undo. */
    private static final class Step extends AbstractCommand {
        private final String name;
        private final List<String> log;
        boolean failExecute;
        boolean failUndo;

        Step(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        protected void doExecute() {
            if (failExecute) {
                throw new IllegalStateException(name + " failed");
            }
            log.add("do " + name);
        }

        @Override
        protected void doUndo() {
            if (failUndo) {
                throw new IllegalStateException(name + " undo failed");
            }
            log.add("undo " + name);
        }

        @Override
        public String description() {
            return name;
        }
    }

    private final List<String> log = new ArrayList<>();

    private List<Step> steps(int count) {
        List<Step> steps = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            steps.add(new Step("s" + i, log));
        }
        return steps;
    }

    @Test
    void executesInOrderAndUndoesInReverse() {
        MacroCommand macro = new MacroCommand("Three steps", List.copyOf(steps(3)));

        macro.execute();
        assertEquals(List.of("do s1", "do s2", "do s3"), log);

        macro.undo();
        assertEquals(List.of("do s1", "do s2", "do s3", "undo s3", "undo s2", "undo s1"), log);
        assertEquals("Three steps", macro.description());
        assertEquals(3, macro.size());
    }

    @Test
    void canBeExecutedAgainAfterUndo() {
        MacroCommand macro = new MacroCommand("Two steps", List.copyOf(steps(2)));
        macro.execute();
        macro.undo();
        log.clear();

        macro.execute();

        assertEquals(List.of("do s1", "do s2"), log);
    }

    @Test
    void failureInTheSecondChildRollsBackTheFirst() {
        List<Step> steps = steps(3);
        steps.get(1).failExecute = true;
        MacroCommand macro = new MacroCommand("Fails", List.copyOf(steps));

        IllegalStateException e = assertThrows(IllegalStateException.class, macro::execute);

        assertEquals("s2 failed", e.getMessage());
        assertEquals(List.of("do s1", "undo s1"), log);
        // The macro stays not-executed: undo is refused and execute may be tried again.
        assertThrows(IllegalStateException.class, macro::undo);
        steps.get(1).failExecute = false;
        macro.execute();
        assertEquals(List.of("do s1", "undo s1", "do s1", "do s2", "do s3"), log);
    }

    @Test
    void rollbackFailureIsSuppressedOnTheOriginal() {
        List<Step> steps = steps(3);
        steps.get(0).failUndo = true;
        steps.get(2).failExecute = true;
        MacroCommand macro = new MacroCommand("Fails twice", List.copyOf(steps));

        IllegalStateException e = assertThrows(IllegalStateException.class, macro::execute);

        assertEquals("s3 failed", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertEquals("s1 undo failed", e.getSuppressed()[0].getMessage());
        assertEquals(List.of("do s1", "do s2", "undo s2"), log);
    }

    @Test
    void emptyOrNullChildrenAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MacroCommand("Nothing", List.of()));
        assertThrows(NullPointerException.class, () -> new MacroCommand(null, List.copyOf(steps(1))));
        assertThrows(NullPointerException.class, () -> new MacroCommand("x", null));
        List<Command> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new MacroCommand("x", withNull));
    }

    @Test
    void childListIsCopied() {
        List<Command> children = new ArrayList<>(steps(1));
        MacroCommand macro = new MacroCommand("Copy", children);
        children.add(new Step("late", log));

        macro.execute();

        assertEquals(List.of("do s1"), log);
    }

    @Test
    void doubleExecuteAndEarlyUndoFollowTheCommandContract() {
        MacroCommand macro = new MacroCommand("Once", List.copyOf(steps(1)));

        assertThrows(IllegalStateException.class, macro::undo);
        macro.execute();
        assertThrows(IllegalStateException.class, macro::execute);
    }

    @Test
    void realReplaceMealCommandsUndoAndRedoAsOneStep() {
        MealPlan plan = new MealPlan("Weekly Plan", 2);
        Recipe soup = new Recipe("T1", "Soup", 1, 10, List.of("Cook"),
                List.of(new Ingredient("Lentils", 100, Unit.GRAM, 300, 20, 40, 5, 1.00)));
        Meal old = new Meal(MealType.DINNER, "Old dinner", List.of(soup));
        plan.setMeal(2, MealType.DINNER, old);
        Meal lunch = new Meal(MealType.LUNCH, "Soup", List.of(soup));
        Meal dinner = new Meal(MealType.DINNER, "Soup", List.of(soup));
        CommandHistory history = new CommandHistory();

        history.pushAndExecute(new MacroCommand("Add two meals", List.of(
                new ReplaceMealCommand(plan, 1, MealType.LUNCH, lunch),
                new ReplaceMealCommand(plan, 2, MealType.DINNER, dinner))));

        assertEquals(1, history.undoSize());
        assertSame(lunch, plan.getMeal(1, MealType.LUNCH).orElseThrow());
        assertSame(dinner, plan.getMeal(2, MealType.DINNER).orElseThrow());

        assertTrue(history.undo());
        assertTrue(plan.getMeal(1, MealType.LUNCH).isEmpty());
        assertSame(old, plan.getMeal(2, MealType.DINNER).orElseThrow());

        assertTrue(history.redo());
        assertSame(lunch, plan.getMeal(1, MealType.LUNCH).orElseThrow());
        assertSame(dinner, plan.getMeal(2, MealType.DINNER).orElseThrow());
        assertEquals("Add two meals", history.nextUndoDescription().orElseThrow());
    }
}
