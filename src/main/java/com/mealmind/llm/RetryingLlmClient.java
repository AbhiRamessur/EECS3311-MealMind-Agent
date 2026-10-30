package com.mealmind.llm;

import java.time.Duration;
import java.util.Objects;

/**
 * Wraps another {@link LLMClient} and retries transient failures (a Decorator). By default it makes
 * two attempts in total, which is the single automatic retry from Stage 1 (UC05 alternate flow 4a).
 *
 * <p>Only failures whose {@link LlmException#isRetryable()} is true are retried (unreachable,
 * timeout, rate limit, server error); others are thrown at once. The wait before attempt
 * {@code n + 1} is {@code n * backoff}. When every attempt fails, the last exception is thrown.
 */
public final class RetryingLlmClient implements LLMClient {

    /** Waits between attempts; replaceable so tests do not really sleep. */
    @FunctionalInterface
    public interface Sleeper {
        /**
         * Pauses the current thread.
         *
         * @param duration how long to wait
         * @throws InterruptedException if interrupted while waiting
         */
        void sleep(Duration duration) throws InterruptedException;
    }

    /** Default number of attempts: the first call plus one retry. */
    public static final int DEFAULT_MAX_ATTEMPTS = 2;
    /** Default wait before the first retry. */
    public static final Duration DEFAULT_BACKOFF = Duration.ofMillis(500);

    private final LLMClient delegate;
    private final int maxAttempts;
    private final Duration backoff;
    private final Sleeper sleeper;

    /**
     * Wraps {@code delegate} with the defaults: 2 attempts, 500 ms backoff, real sleeping.
     *
     * @param delegate the client to call
     */
    public RetryingLlmClient(LLMClient delegate) {
        this(delegate, DEFAULT_MAX_ATTEMPTS, DEFAULT_BACKOFF, Thread::sleep);
    }

    /**
     * Wraps {@code delegate} with custom retry settings.
     *
     * @param delegate    the client to call
     * @param maxAttempts total attempts, at least 1
     * @param backoff     base wait between attempts, not negative
     * @param sleeper     how to wait
     * @throws IllegalArgumentException if {@code maxAttempts < 1} or {@code backoff} is negative
     * @throws NullPointerException     if an argument is null
     */
    public RetryingLlmClient(LLMClient delegate, int maxAttempts, Duration backoff, Sleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        Objects.requireNonNull(backoff, "backoff");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }
        if (backoff.isNegative()) {
            throw new IllegalArgumentException("backoff must not be negative");
        }
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.complete(request);
            } catch (LlmException e) {
                if (!e.isRetryable() || attempt >= maxAttempts) {
                    throw e;
                }
                pause(backoff.multipliedBy(attempt), e);
            }
        }
    }

    private void pause(Duration wait, LlmException lastFailure) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LlmException interrupted = new LlmException(LlmErrorKind.INTERRUPTED,
                    "The AI request was interrupted while waiting to retry", e);
            interrupted.addSuppressed(lastFailure);
            throw interrupted;
        }
    }
}
