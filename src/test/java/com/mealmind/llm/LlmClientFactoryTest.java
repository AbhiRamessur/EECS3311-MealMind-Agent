package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.config.AppConfig;
import com.mealmind.config.ConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmClientFactoryTest {

    /** Built at runtime so no key-shaped literal exists in the source. */
    private static final String FAKE_KEY = "sk" + "-" + "x".repeat(30);
    private static final String OPENAI_OK = "{\"model\":\"gpt-4o\",\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";

    @TempDir
    Path dir;

    private FakeLlmServer server;

    @BeforeEach
    void start() throws IOException {
        server = new FakeLlmServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    /** Loads config only from the given map: the env file does not exist, so no real .env is read. */
    private AppConfig config(Map<String, String> env) {
        return AppConfig.load(dir.resolve("missing.env"), env);
    }

    @Test
    void openAiConfigBuildsRetryingOpenAiClient() {
        server.enqueueJson(200, OPENAI_OK);
        LLMClient client = LlmClientFactory.create(config(Map.of("LLM_API_KEY", FAKE_KEY)), server.baseUrl());

        assertInstanceOf(RetryingLlmClient.class, client);
        assertEquals("OpenAI", client.providerName());
        assertEquals("gpt-4o", client.modelName());
        assertEquals("OK", client.complete(ScriptedLlmClient.anyRequest()).content());
        assertEquals("/v1/chat/completions", server.lastRequest().path());
        assertEquals("Bearer " + FAKE_KEY, server.lastRequest().header("Authorization"));
    }

    @Test
    void ollamaConfigBuildsOllamaClient() {
        server.enqueueJson(200, "{\"message\":{\"content\":\"OK\"}}");
        LLMClient client = LlmClientFactory.create(config(Map.of("LLM_PROVIDER", "ollama",
                "OLLAMA_BASE_URL", server.baseUrl())));

        assertEquals("Ollama", client.providerName());
        assertEquals("llama3", client.modelName());
        assertEquals("OK", client.complete(ScriptedLlmClient.anyRequest()).content());
        assertEquals("/api/chat", server.lastRequest().path());
        assertNull(server.lastRequest().header("Authorization"));
    }

    @Test
    void resultRetriesOnceAfterServerError() {
        // Uses the package-private overload with a 1 ms backoff so the test stays fast and deterministic;
        // the default 500 ms backoff is covered by RetryingLlmClient's own tests.
        server.enqueueJson(500, "{}").enqueueJson(200, OPENAI_OK);
        LLMClient client = LlmClientFactory.create(config(Map.of("LLM_API_KEY", FAKE_KEY, "LLM_TIMEOUT_SECONDS", "5")),
                server.baseUrl(), Duration.ofMillis(1));

        assertEquals("OK", client.complete(ScriptedLlmClient.anyRequest()).content());
        assertEquals(2, server.requests().size());
    }

    @Test
    void openAiWithoutKeyFailsWithoutKeyText() {
        // AppConfig already refuses an OpenAI config without a real key, so the factory never receives one;
        // this checks that the whole config-to-client path fails with a ConfigurationException free of key text.
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> LlmClientFactory.create(config(Map.of("LLM_API_KEY", "YOUR_LOCAL_API_KEY_HERE"))));

        assertFalse(e.getMessage().contains("YOUR_LOCAL_API_KEY_HERE"), e.getMessage());
    }
}
