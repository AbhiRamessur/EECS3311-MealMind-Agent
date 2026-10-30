package com.mealmind.llm;

/**
 * A completion returned by a provider.
 *
 * @param content the reply text, not blank (an empty completion is reported as a bad response instead)
 * @param usage   tokens used
 * @param model   the model that answered, as reported by the provider
 */
public record LlmResponse(String content, TokenUsage usage, String model) {

    /**
     * Validates the response.
     *
     * @throws IllegalArgumentException if {@code content} or {@code model} is null or blank, or {@code usage} is null
     */
    public LlmResponse {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Response content must not be blank");
        }
        if (usage == null) {
            throw new IllegalArgumentException("Response usage must not be null");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Response model must not be blank");
        }
    }
}
