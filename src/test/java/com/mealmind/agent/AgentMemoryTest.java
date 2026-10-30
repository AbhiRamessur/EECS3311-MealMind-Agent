package com.mealmind.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.llm.ChatMessage;
import com.mealmind.llm.Role;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AgentMemoryTest {

    @Test
    void windowDropsOldestMessages() {
        AgentMemory memory = new AgentMemory(4, 100, 5);
        for (int i = 1; i <= 3; i++) {
            memory.addUserMessage("question " + i);
            memory.addAssistantMessage("answer " + i);
        }

        List<ChatMessage> history = memory.history();

        assertEquals(4, history.size());
        assertEquals(List.of("question 2", "answer 2", "question 3", "answer 3"),
                history.stream().map(ChatMessage::content).toList());
    }

    @Test
    void historyNeverStartsWithAssistantMessage() {
        AgentMemory memory = new AgentMemory(3, 100, 5);
        memory.addUserMessage("q1");
        memory.addAssistantMessage("a1");
        memory.addUserMessage("q2");
        memory.addAssistantMessage("a2"); // window [a1, q2, a2] -> leading a1 is dropped too

        assertEquals(Role.USER, memory.history().get(0).role());
        assertEquals(List.of("q2", "a2"), memory.history().stream().map(ChatMessage::content).toList());
        assertEquals(2, memory.size());
    }

    @Test
    void longMessagesAreCutToTheLimit() {
        AgentMemory memory = new AgentMemory(4, 100, 5);

        memory.addUserMessage("x".repeat(250));

        String stored = memory.history().get(0).content();
        assertEquals(100, stored.length());
        assertTrue(stored.endsWith("…"));
    }

    @Test
    void blankMessagesAreRejected() {
        AgentMemory memory = new AgentMemory();

        assertThrows(IllegalArgumentException.class, () -> memory.addUserMessage(" "));
        assertThrows(IllegalArgumentException.class, () -> memory.addAssistantMessage(null));
    }

    @Test
    void historyIsAnUnmodifiableSnapshot() {
        AgentMemory memory = new AgentMemory();
        memory.addUserMessage("first");
        List<ChatMessage> snapshot = memory.history();

        memory.addAssistantMessage("second");

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(ChatMessage.user("x")));
    }

    @Test
    void factsAreCleanedDeduplicatedBoundedAndForgettable() {
        AgentMemory memory = new AgentMemory(4, 100, 2);

        assertTrue(memory.remember("  Prefers   quick   breakfasts "));
        assertFalse(memory.remember("prefers quick BREAKFASTS"));
        assertTrue(memory.remember("Dislikes mushrooms"));
        assertTrue(memory.remember("Cooks for two"));

        assertEquals(List.of("Dislikes mushrooms", "Cooks for two"), memory.facts());
        assertTrue(memory.forget("DISLIKES   mushrooms"));
        assertFalse(memory.forget("never said"));
        assertEquals(List.of("Cooks for two"), memory.facts());
        assertThrows(IllegalArgumentException.class, () -> memory.remember("x".repeat(201)));
        assertThrows(IllegalArgumentException.class, () -> memory.remember(" "));
        assertThrows(UnsupportedOperationException.class, () -> memory.facts().add("x"));
    }

    @Test
    void clearConversationKeepsFactsAndClearAllClearsBoth() {
        AgentMemory memory = new AgentMemory();
        memory.addUserMessage("hello");
        memory.remember("Vegetarian on weekdays");

        memory.clearConversation();
        assertEquals(0, memory.size());
        assertEquals(1, memory.facts().size());

        memory.addUserMessage("again");
        memory.clearAll();
        assertEquals(0, memory.size());
        assertEquals(List.of(), memory.facts());
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AgentMemory(1, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new AgentMemory(2, 99, 1));
        assertThrows(IllegalArgumentException.class, () -> new AgentMemory(2, 100, 0));
    }

    @Test
    void concurrentAddsStayWithinLimits() throws Exception {
        AgentMemory memory = new AgentMemory(10, 100, 5);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int thread = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    memory.addUserMessage("u" + thread + "-" + i);
                    memory.addAssistantMessage("a" + thread + "-" + i);
                    memory.remember("fact " + thread + "-" + i);
                    assertTrue(memory.history().size() <= 10);
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS); // rethrows any failure from a worker
        }
        pool.shutdown();

        assertTrue(memory.size() <= 10);
        assertEquals(5, memory.facts().size());
        assertEquals(Role.USER, memory.history().get(0).role());
    }
}
