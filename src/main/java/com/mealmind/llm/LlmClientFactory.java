package com.mealmind.llm;

import com.mealmind.config.AppConfig;
import com.mealmind.config.ConfigurationException;
import com.mealmind.config.LlmProvider;
import java.time.Duration;
import java.util.Objects;

/**
 * Builds the configured {@link LLMClient}. This is the only place where the provider is chosen
 * from {@link AppConfig}; domain code only ever sees the {@code LLMClient} interface. The result is
 * always wrapped in a {@link RetryingLlmClient}. Creating a client never contacts the network.
 */
public final class LlmClientFactory {

    private LlmClientFactory() {
    }

    /**
     * Creates the client for {@code config}, using the public OpenAI endpoint.
     *
     * @param config the loaded configuration
     * @return a retrying OpenAI or Ollama client
     * @throws ConfigurationException   if OpenAI is selected without an API key
     * @throws IllegalArgumentException if a configured URL or model is invalid
     */
    public static LLMClient create(AppConfig config) {
        return create(config, OpenAiClient.DEFAULT_BASE_URL);
    }

    /**
     * Creates the client for {@code config} with a custom OpenAI base URL (used by tests).
     *
     * @param config        the loaded configuration
     * @param openAiBaseUrl where to send OpenAI requests
     * @return a retrying OpenAI or Ollama client
     * @throws ConfigurationException   if OpenAI is selected without an API key
     * @throws IllegalArgumentException if a configured URL or model is invalid
     */
    public static LLMClient create(AppConfig config, String openAiBaseUrl) {
        return create(config, openAiBaseUrl, RetryingLlmClient.DEFAULT_BACKOFF);
    }

    /** As {@link #create(AppConfig, String)} with a custom retry backoff, so tests stay fast. */
    static LLMClient create(AppConfig config, String openAiBaseUrl, Duration backoff) {
        Objects.requireNonNull(config, "config");
        Duration timeout = Duration.ofSeconds(config.getTimeoutSeconds());
        LLMClient base;
        if (config.getProvider() == LlmProvider.OPENAI) {
            if (!config.hasApiKey()) {
                throw new ConfigurationException("No OpenAI API key configured. Set LLM_API_KEY in .env or use LLM_PROVIDER=ollama.");
            }
            base = new OpenAiClient(openAiBaseUrl, config.getApiKey(), config.getModel(), timeout);
        } else {
            base = new OllamaClient(config.getOllamaBaseUrl(), config.getModel(), timeout);
        }
        return new RetryingLlmClient(base, RetryingLlmClient.DEFAULT_MAX_ATTEMPTS, backoff, Thread::sleep);
    }
}
