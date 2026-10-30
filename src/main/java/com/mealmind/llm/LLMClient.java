package com.mealmind.llm;

/**
 * The Strategy interface of the <strong>Strategy pattern</strong> for talking to language models.
 *
 * <p>Roles: {@code LLMClient} is the Strategy; {@link OpenAiClient} (cloud, primary) and
 * {@link OllamaClient} (local, offline fallback) are ConcreteStrategies; {@link LlmService} is the
 * Context, which holds the active strategy and can switch it at runtime
 * ({@link LlmService#useClient}). Domain code depends only on this interface, so it works the same
 * whichever provider is configured. {@link RetryingLlmClient} wraps any strategy to add retries.
 */
public interface LLMClient {

    /**
     * Returns the provider's display name, e.g. {@code "OpenAI"}.
     *
     * @return the provider name
     */
    String providerName();

    /**
     * Returns the configured model, e.g. {@code "gpt-4o"}.
     *
     * @return the model name
     */
    String modelName();

    /**
     * Sends a request and waits for the completion.
     *
     * @param request the conversation and settings
     * @return the provider's reply
     * @throws LlmException if the call fails for any reason; the message is user-readable and secret-free
     */
    LlmResponse complete(LlmRequest request) throws LlmException;
}
