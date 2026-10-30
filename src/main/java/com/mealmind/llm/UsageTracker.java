package com.mealmind.llm;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts LLM calls and tokens for the session. Thread-safe.
 *
 * <p>Only tokens are tracked, not money: prices change and differ per model, and the project's
 * development cost is recorded separately in the AI collaboration log.
 */
public final class UsageTracker {

    /**
     * An immutable copy of the counters.
     *
     * @param successfulCalls  completed calls
     * @param failedCalls      calls that ended in an {@link LlmException}
     * @param promptTokens     prompt tokens over all successful calls
     * @param completionTokens completion tokens over all successful calls
     */
    public record Snapshot(long successfulCalls, long failedCalls, long promptTokens, long completionTokens) {

        /**
         * Returns prompt plus completion tokens.
         *
         * @return the total
         */
        public long totalTokens() {
            return promptTokens + completionTokens;
        }
    }

    private final AtomicLong successfulCalls = new AtomicLong();
    private final AtomicLong failedCalls = new AtomicLong();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();

    /**
     * Records a successful call.
     *
     * @param usage the tokens it used
     */
    public void recordSuccess(TokenUsage usage) {
        successfulCalls.incrementAndGet();
        promptTokens.addAndGet(usage.promptTokens());
        completionTokens.addAndGet(usage.completionTokens());
    }

    /** Records a failed call. */
    public void recordFailure() {
        failedCalls.incrementAndGet();
    }

    /**
     * Returns the current counts.
     *
     * @return an immutable snapshot
     */
    public Snapshot snapshot() {
        return new Snapshot(successfulCalls.get(), failedCalls.get(), promptTokens.get(), completionTokens.get());
    }

    /** Sets every counter back to zero. */
    public void reset() {
        successfulCalls.set(0);
        failedCalls.set(0);
        promptTokens.set(0);
        completionTokens.set(0);
    }
}
