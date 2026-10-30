package com.mealmind.agent;

import com.mealmind.llm.ChatMessage;
import com.mealmind.llm.Role;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The agent's short-term memory for one session: a bounded window of recent conversation turns and
 * a short list of facts (durable preference notes, e.g. "prefers quick breakfasts") that the agent
 * will add to its prompts.
 *
 * <p>It lives in memory only. Persisted preference memory (ratings and favourites, F12) is added in
 * a later commit and will feed {@link #facts()} from stored ratings. All methods are thread-safe.
 */
public final class AgentMemory {

    /** Longest fact, in characters. */
    public static final int MAX_FACT_LENGTH = 200;

    private final int maxMessages;
    private final int maxCharsPerMessage;
    private final int maxFacts;
    private final Deque<ChatMessage> conversation = new ArrayDeque<>();
    private final List<String> facts = new ArrayList<>();

    /** Creates a memory keeping 20 messages of at most 4000 characters, and 20 facts. */
    public AgentMemory() {
        this(20, 4000, 20);
    }

    /**
     * Creates a memory with custom limits.
     *
     * @param maxMessages        conversation messages kept, at least 2
     * @param maxCharsPerMessage longest stored message, at least 100
     * @param maxFacts           facts kept, at least 1
     * @throws IllegalArgumentException if a limit is too small
     */
    public AgentMemory(int maxMessages, int maxCharsPerMessage, int maxFacts) {
        if (maxMessages < 2) {
            throw new IllegalArgumentException("maxMessages must be at least 2, got " + maxMessages);
        }
        if (maxCharsPerMessage < 100) {
            throw new IllegalArgumentException("maxCharsPerMessage must be at least 100, got " + maxCharsPerMessage);
        }
        if (maxFacts < 1) {
            throw new IllegalArgumentException("maxFacts must be at least 1, got " + maxFacts);
        }
        this.maxMessages = maxMessages;
        this.maxCharsPerMessage = maxCharsPerMessage;
        this.maxFacts = maxFacts;
    }

    // ---- conversation window ----

    /**
     * Records something the user said.
     *
     * @param content the message; cut to the length limit (ending with "…") if longer
     * @throws IllegalArgumentException if {@code content} is null or blank
     */
    public synchronized void addUserMessage(String content) {
        add(Role.USER, content);
    }

    /**
     * Records something the assistant replied.
     *
     * @param content the message; cut to the length limit (ending with "…") if longer
     * @throws IllegalArgumentException if {@code content} is null or blank
     */
    public synchronized void addAssistantMessage(String content) {
        add(Role.ASSISTANT, content);
    }

    private void add(Role role, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }
        String stored = content.length() <= maxCharsPerMessage
                ? content : content.substring(0, maxCharsPerMessage - 1) + "…";
        conversation.addLast(new ChatMessage(role, stored));
        while (conversation.size() > maxMessages) {
            conversation.removeFirst();
        }
        // Providers handle a history that starts with the user's turn better.
        while (!conversation.isEmpty() && conversation.peekFirst().role() == Role.ASSISTANT) {
            conversation.removeFirst();
        }
    }

    /**
     * Returns the remembered conversation, oldest first; it always starts with a user message.
     *
     * @return an unmodifiable snapshot
     */
    public synchronized List<ChatMessage> history() {
        return List.copyOf(conversation);
    }

    /**
     * Returns the number of remembered messages.
     *
     * @return the size of the conversation window
     */
    public synchronized int size() {
        return conversation.size();
    }

    // ---- facts ----

    /**
     * Remembers a fact. Whitespace is trimmed and collapsed; a fact equal to an existing one
     * (ignoring case) is not added again. When full, the oldest fact is dropped.
     *
     * @param fact the fact, at most {@value #MAX_FACT_LENGTH} characters
     * @return {@code true} if added, {@code false} if it was already known
     * @throws IllegalArgumentException if {@code fact} is null, blank or too long
     */
    public synchronized boolean remember(String fact) {
        String clean = cleanFact(fact);
        if (clean.length() > MAX_FACT_LENGTH) {
            throw new IllegalArgumentException("A fact must be at most " + MAX_FACT_LENGTH + " characters");
        }
        if (facts.stream().anyMatch(known -> known.equalsIgnoreCase(clean))) {
            return false;
        }
        facts.add(clean);
        if (facts.size() > maxFacts) {
            facts.remove(0);
        }
        return true;
    }

    /**
     * Forgets a fact, ignoring case and extra whitespace.
     *
     * @param fact the fact
     * @return {@code true} if it was known
     * @throws IllegalArgumentException if {@code fact} is null or blank
     */
    public synchronized boolean forget(String fact) {
        String clean = cleanFact(fact);
        return facts.removeIf(known -> known.equalsIgnoreCase(clean));
    }

    private static String cleanFact(String fact) {
        if (fact == null || fact.isBlank()) {
            throw new IllegalArgumentException("A fact must not be blank");
        }
        return fact.trim().replaceAll("\\s+", " ");
    }

    /**
     * Returns the remembered facts in the order they were added.
     *
     * @return an unmodifiable snapshot
     */
    public synchronized List<String> facts() {
        return List.copyOf(facts);
    }

    /** Forgets the conversation but keeps the facts. */
    public synchronized void clearConversation() {
        conversation.clear();
    }

    /** Forgets the conversation and the facts. */
    public synchronized void clearAll() {
        conversation.clear();
        facts.clear();
    }
}
