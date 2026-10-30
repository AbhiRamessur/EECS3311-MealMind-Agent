package com.mealmind.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable application configuration, resolved once at startup.
 *
 * <p>Each setting is taken from a real environment variable if set, otherwise from the
 * {@code .env} file, otherwise from a built-in default. The API key is never hard-coded and never
 * appears in {@link #toString()} or in exception messages.
 */
public final class AppConfig {

    /** Placeholder API key shipped in {@code .env.example}; treated as "no key". */
    public static final String API_KEY_PLACEHOLDER = "YOUR_LOCAL_API_KEY_HERE";

    private static final String KEY_PROVIDER = "LLM_PROVIDER";
    private static final String KEY_API_KEY = "LLM_API_KEY";
    private static final String KEY_MODEL = "LLM_MODEL";
    private static final String KEY_OLLAMA_BASE_URL = "OLLAMA_BASE_URL";
    private static final String KEY_TIMEOUT_SECONDS = "LLM_TIMEOUT_SECONDS";

    private static final String DEFAULT_PROVIDER = "openai";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o";
    private static final String DEFAULT_OLLAMA_MODEL = "llama3";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_TIMEOUT_SECONDS = "30";

    private final LlmProvider provider;
    private final String apiKey;
    private final String model;
    private final String ollamaBaseUrl;
    private final int timeoutSeconds;

    private AppConfig(LlmProvider provider, String apiKey, String model, String ollamaBaseUrl,
                      int timeoutSeconds) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Loads configuration from {@code ./.env} and the process environment.
     *
     * @return the validated configuration
     * @throws ConfigurationException if the configuration is invalid
     */
    public static AppConfig load() {
        return load(Path.of(".env"), System.getenv());
    }

    /**
     * Loads configuration from the given {@code .env} file and environment map.
     *
     * @param envFile the {@code .env} file; a missing file is treated as empty
     * @param env     environment variables, which override values from {@code envFile}
     * @return the validated configuration
     * @throws ConfigurationException if the configuration is invalid
     */
    public static AppConfig load(Path envFile, Map<String, String> env) {
        Map<String, String> fileValues = DotEnvParser.parse(envFile);

        LlmProvider provider = parseProvider(lookup(KEY_PROVIDER, env, fileValues, DEFAULT_PROVIDER));
        String apiKey = lookup(KEY_API_KEY, env, fileValues, null);
        // The default model depends on the provider: Ollama has no model called gpt-4o.
        String defaultModel = provider == LlmProvider.OLLAMA ? DEFAULT_OLLAMA_MODEL : DEFAULT_OPENAI_MODEL;
        String model = lookup(KEY_MODEL, env, fileValues, defaultModel);
        String ollamaBaseUrl = lookup(KEY_OLLAMA_BASE_URL, env, fileValues, DEFAULT_OLLAMA_BASE_URL);
        int timeoutSeconds = parseTimeout(lookup(KEY_TIMEOUT_SECONDS, env, fileValues, DEFAULT_TIMEOUT_SECONDS));

        if (API_KEY_PLACEHOLDER.equals(apiKey)) {
            apiKey = null;
        }
        if (provider == LlmProvider.OPENAI && apiKey == null) {
            throw new ConfigurationException("No OpenAI API key configured. Copy .env.example to .env and set "
                    + KEY_API_KEY + " to your own key, or set " + KEY_PROVIDER + " to ollama.");
        }
        return new AppConfig(provider, apiKey, model, ollamaBaseUrl, timeoutSeconds);
    }

    /** Returns the first non-blank value from env, then the .env file, then the default. */
    private static String lookup(String key, Map<String, String> env, Map<String, String> fileValues,
                                 String defaultValue) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            value = fileValues.get(key);
        }
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static LlmProvider parseProvider(String value) {
        try {
            return LlmProvider.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Unknown " + KEY_PROVIDER + " '" + value
                    + "'. Expected openai or ollama.");
        }
    }

    private static int parseTimeout(String value) {
        try {
            int seconds = Integer.parseInt(value);
            if (seconds > 0) {
                return seconds;
            }
        } catch (NumberFormatException e) {
            // fall through to the error below
        }
        throw new ConfigurationException(KEY_TIMEOUT_SECONDS + " must be a positive whole number of seconds.");
    }

    /**
     * Returns the selected LLM provider.
     *
     * @return the provider
     */
    public LlmProvider getProvider() {
        return provider;
    }

    /**
     * Returns whether an API key is configured.
     *
     * @return {@code true} if a non-placeholder key is set
     */
    public boolean hasApiKey() {
        return apiKey != null;
    }

    /**
     * Returns the API key for the LLM client. Never log or display this value.
     *
     * @return the key, or {@code null} if none is configured (allowed only for Ollama)
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the model name sent to the provider.
     *
     * @return the model name
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns the base URL of the local Ollama server.
     *
     * @return the Ollama base URL
     */
    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    /**
     * Returns the LLM request timeout.
     *
     * @return the timeout in seconds, always positive
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Describes this configuration with the API key redacted.
     *
     * @return a string showing {@code apiKey=<set>} or {@code apiKey=<none>}, never the key itself
     */
    @Override
    public String toString() {
        return "AppConfig{provider=" + provider
                + ", apiKey=" + (hasApiKey() ? "<set>" : "<none>")
                + ", model=" + model
                + ", ollamaBaseUrl=" + ollamaBaseUrl
                + ", timeoutSeconds=" + timeoutSeconds
                + "}";
    }
}
