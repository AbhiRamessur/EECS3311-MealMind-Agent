package com.mealmind.config;

/**
 * The LLM back ends MealMind can talk to.
 *
 * <p>{@link #OPENAI} (GPT-4o over the REST API) is the primary provider; {@link #OLLAMA} is the
 * local, offline fallback that needs no API key (see Stage 1 design report, section 1.1).
 */
public enum LlmProvider {
    /** OpenAI cloud API; requires an API key. */
    OPENAI,
    /** Local Ollama server; no API key needed. */
    OLLAMA
}
