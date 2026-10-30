package com.mealmind.llm;

import java.util.List;

/**
 * A provider-neutral completion request.
 *
 * <p>{@code jsonMode} asks the provider to answer with a JSON object. With OpenAI, the messages must
 * also mention the word "JSON" or the API rejects the request; this is documented, not enforced.
 *
 * @param messages    the conversation so far, oldest first (copied, not empty)
 * @param temperature randomness, from 0 (deterministic) to 2
 * @param maxTokens   the most tokens to generate, or null for the provider's default
 * @param jsonMode    whether to request a JSON object as the reply
 */
public record LlmRequest(List<ChatMessage> messages, double temperature, Integer maxTokens, boolean jsonMode) {

    /** Default temperature: low, for consistent, structured answers. */
    public static final double DEFAULT_TEMPERATURE = 0.2;

    /**
     * Validates and copies the request.
     *
     * @throws IllegalArgumentException if {@code messages} is null or empty, the temperature is not
     *                                  in [0, 2], or {@code maxTokens} is less than 1
     */
    public LlmRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("A request needs at least one message");
        }
        messages = List.copyOf(messages);
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Temperature must be between 0 and 2, got " + temperature);
        }
        if (maxTokens != null && maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be at least 1, got " + maxTokens);
        }
    }

    /**
     * Creates a request with default settings: temperature 0.2, no token limit, plain text.
     *
     * @param messages the conversation
     * @return the request
     */
    public static LlmRequest of(List<ChatMessage> messages) {
        return new LlmRequest(messages, DEFAULT_TEMPERATURE, null, false);
    }

    /**
     * Returns a copy with JSON mode switched on or off.
     *
     * @param enabled whether to request a JSON object
     * @return the modified copy
     */
    public LlmRequest withJsonMode(boolean enabled) {
        return new LlmRequest(messages, temperature, maxTokens, enabled);
    }

    /**
     * Returns a copy with a different temperature.
     *
     * @param value the temperature, from 0 to 2
     * @return the modified copy
     */
    public LlmRequest withTemperature(double value) {
        return new LlmRequest(messages, value, maxTokens, jsonMode);
    }

    /**
     * Returns a copy with a different token limit.
     *
     * @param value the limit, at least 1, or null for the provider's default
     * @return the modified copy
     */
    public LlmRequest withMaxTokens(Integer value) {
        return new LlmRequest(messages, temperature, value, jsonMode);
    }
}
