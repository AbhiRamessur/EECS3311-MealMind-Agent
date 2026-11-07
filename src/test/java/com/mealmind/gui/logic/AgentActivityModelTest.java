package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.agent.AgentEvent;
import com.mealmind.agent.AgentEventType;
import com.mealmind.llm.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentActivityModelTest {

    private final AtomicInteger changes = new AtomicInteger();
    private final List<String> uiLog = new ArrayList<>();

    private AgentActivityModel model(int maxLines) {
        return new AgentActivityModel(task -> {
            synchronized (uiLog) {
                uiLog.add("ui");
            }
            task.run();
        }, changes::incrementAndGet, maxLines);
    }

    @Test
    void formatsLinesSkipsErrorsAndForwardsThroughTheUiThread() {
        AgentActivityModel model = model(10);

        model.accept(new AgentEvent(AgentEventType.THINKING, "Asking the model (call 1)"));
        model.accept(new AgentEvent(AgentEventType.ERROR, "boom"));
        model.accept(new AgentEvent(AgentEventType.TOOL_CALL, "Calling tool recipe_search"));

        assertEquals(List.of("[agent] Asking the model (call 1)", "[agent] Calling tool recipe_search"), model.lines());
        assertEquals(2, changes.get());
        assertEquals(List.of("ui", "ui"), uiLog);
        assertThrows(UnsupportedOperationException.class, () -> model.lines().add("x"));
    }

    @Test
    void boundedListDropsTheOldest() {
        AgentActivityModel model = model(3);
        for (int i = 1; i <= 5; i++) {
            model.accept(new AgentEvent(AgentEventType.THINKING, "step " + i));
        }

        assertEquals(List.of("[agent] step 3", "[agent] step 4", "[agent] step 5"), model.lines());
    }

    @Test
    void manyThreadsNeverThrowOrExceedTheBound() throws Exception {
        AgentActivityModel model = model(50);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < 200; i++) {
                        model.accept(new AgentEvent(AgentEventType.THINKING, "line " + i));
                        assertTrue(model.lines().size() <= 50);
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertEquals(List.of(), failures);
        assertEquals(50, model.lines().size());
        assertEquals(1600, changes.get());
    }

    @Test
    void clearAndNotes() {
        AgentActivityModel model = model(10);
        model.accept(new AgentEvent(AgentEventType.THINKING, "x"));

        model.clear();
        model.addNote("  Agent: 1 model calls \n ");

        assertEquals(List.of("Agent: 1 model calls"), model.lines());
        assertThrows(IllegalArgumentException.class, () -> model(0));
    }

    @Test
    void statsLineEqualsTheCliText() {
        assertEquals("Agent: 2 model calls, 1 tool calls, 0 repairs, 1 safety retries; tokens: 300 prompt + 120 completion",
                AgentActivityModel.statsLine(2, 1, 0, 1, new TokenUsage(300, 120)));
    }
}
