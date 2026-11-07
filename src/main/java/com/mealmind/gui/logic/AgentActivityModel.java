package com.mealmind.gui.logic;

import com.mealmind.agent.AgentEvent;
import com.mealmind.agent.AgentEventType;
import com.mealmind.llm.TokenUsage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The Agent Activity log (Stage 1 "Agent Activity live log panel"): a bounded list of lines such as
 * "[agent] Asking the model (call 1)". {@link #accept} may be called from any thread (agent events arrive
 * on the background thread); the change callback always runs through the {@link UiThread}. ERROR events are
 * skipped because the error itself is shown separately, as in the CLI.
 */
public final class AgentActivityModel {

    /** The default number of lines kept. */
    public static final int DEFAULT_MAX_LINES = 200;

    private final UiThread ui;
    private final Runnable onChange;
    private final int maxLines;
    private final Deque<String> lines = new ArrayDeque<>();

    /**
     * Creates the model.
     *
     * @param ui       where {@code onChange} runs
     * @param onChange the view's refresh action
     * @param maxLines how many lines to keep (the oldest are dropped), at least 1
     * @throws IllegalArgumentException if {@code maxLines < 1}
     * @throws NullPointerException     if a reference argument is null
     */
    public AgentActivityModel(UiThread ui, Runnable onChange, int maxLines) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be at least 1, got " + maxLines);
        }
        this.maxLines = maxLines;
    }

    /**
     * Adds an agent event as an "[agent] ..." line, unless it is an ERROR event. Safe from any thread.
     *
     * @param event the event
     */
    public void accept(AgentEvent event) {
        if (event != null && event.type() != AgentEventType.ERROR) {
            add("[agent] " + event.message());
        }
    }

    /**
     * Adds a line that is not an agent event, such as the stats line.
     *
     * @param line the text
     */
    public void addNote(String line) {
        add(StatusMessages.clean(line));
    }

    /** Removes every line. */
    public void clear() {
        synchronized (lines) {
            lines.clear();
        }
        ui.run(onChange);
    }

    /**
     * Returns the current lines, oldest first.
     *
     * @return an unmodifiable snapshot
     */
    public List<String> lines() {
        synchronized (lines) {
            return List.copyOf(lines);
        }
    }

    /**
     * Formats the agent's counters exactly like the CLI's {@code recipe generate} stats line.
     *
     * @param llmCalls      model calls
     * @param toolSteps     tool calls
     * @param repairs       repair prompts
     * @param safetyRetries safety retries
     * @param usage         tokens used
     * @return e.g. "Agent: 2 model calls, 1 tool calls, 0 repairs, 0 safety retries; tokens: 200 prompt + 40 completion"
     */
    public static String statsLine(int llmCalls, int toolSteps, int repairs, int safetyRetries, TokenUsage usage) {
        return "Agent: " + llmCalls + " model calls, " + toolSteps + " tool calls, " + repairs + " repairs, "
                + safetyRetries + " safety retries; tokens: " + usage.promptTokens() + " prompt + "
                + usage.completionTokens() + " completion";
    }

    private void add(String line) {
        synchronized (lines) {
            lines.addLast(line);
            while (lines.size() > maxLines) {
                lines.removeFirst();
            }
        }
        ui.run(onChange);
    }
}
