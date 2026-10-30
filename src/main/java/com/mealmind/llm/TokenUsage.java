package com.mealmind.llm;

/**
 * Tokens used by one completion, as reported by the provider.
 *
 * @param promptTokens     tokens in the request
 * @param completionTokens tokens in the reply
 */
public record TokenUsage(long promptTokens, long completionTokens) {

    /** No tokens (used when the provider does not report usage). */
    public static final TokenUsage ZERO = new TokenUsage(0, 0);

    /**
     * Validates the counts.
     *
     * @throws IllegalArgumentException if a count is negative
     */
    public TokenUsage {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("Token counts must not be negative");
        }
    }

    /**
     * Returns prompt plus completion tokens.
     *
     * @return the total
     */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
