package com.mealmind.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {

    private static final String FAKE_KEY = "test-key-123-not-real";

    @TempDir
    Path dir;

    private Path envFile(String content) throws IOException {
        Path file = dir.resolve(".env");
        Files.writeString(file, content);
        return file;
    }

    private Path noEnvFile() {
        return dir.resolve("missing.env");
    }

    @Test
    void environmentVariableOverridesEnvFile() throws IOException {
        Path file = envFile("LLM_PROVIDER=openai\nLLM_API_KEY=" + FAKE_KEY + "\nLLM_MODEL=from-file\n");

        AppConfig config = AppConfig.load(file, Map.of("LLM_MODEL", "from-env"));

        assertEquals("from-env", config.getModel());
    }

    @Test
    void envFileOverridesDefault() throws IOException {
        Path file = envFile("LLM_PROVIDER=ollama\nLLM_TIMEOUT_SECONDS=45\n");

        AppConfig config = AppConfig.load(file, Map.of());

        assertEquals(45, config.getTimeoutSeconds());
    }

    @Test
    void appliesDefaultsForOllama() {
        AppConfig config = AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "ollama"));

        assertEquals(LlmProvider.OLLAMA, config.getProvider());
        assertEquals("llama3", config.getModel());
        assertEquals("http://localhost:11434", config.getOllamaBaseUrl());
        assertEquals(30, config.getTimeoutSeconds());
    }

    @Test
    void defaultModelDependsOnProviderAndExplicitModelWins() {
        assertEquals("gpt-4o", AppConfig.load(noEnvFile(), Map.of("LLM_API_KEY", FAKE_KEY)).getModel());
        assertEquals("llama3", AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "ollama")).getModel());
        assertEquals("gpt-4o-mini",
                AppConfig.load(noEnvFile(), Map.of("LLM_API_KEY", FAKE_KEY, "LLM_MODEL", "gpt-4o-mini")).getModel());
        assertEquals("deepseek-r1:8b",
                AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "ollama", "LLM_MODEL", "deepseek-r1:8b")).getModel());
    }

    @Test
    void openAiWithMissingKeyThrows() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> AppConfig.load(noEnvFile(), Map.of()));

        assertTrue(e.getMessage().contains(".env.example"));
        assertTrue(e.getMessage().contains("LLM_API_KEY"));
    }

    @Test
    void openAiWithPlaceholderKeyThrows() throws IOException {
        Path file = envFile("LLM_API_KEY=" + AppConfig.API_KEY_PLACEHOLDER + "\n");

        assertThrows(ConfigurationException.class, () -> AppConfig.load(file, Map.of()));
    }

    @Test
    void ollamaWithoutKeySucceeds() {
        AppConfig config = AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "ollama"));

        assertFalse(config.hasApiKey());
        assertNull(config.getApiKey());
    }

    @Test
    void openAiWithKeySucceeds() {
        AppConfig config = AppConfig.load(noEnvFile(), Map.of("LLM_API_KEY", FAKE_KEY));

        assertEquals(LlmProvider.OPENAI, config.getProvider());
        assertTrue(config.hasApiKey());
        assertEquals(FAKE_KEY, config.getApiKey());
    }

    @Test
    void unknownProviderThrows() {
        assertThrows(ConfigurationException.class,
                () -> AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "gemini")));
    }

    @Test
    void badTimeoutThrows() {
        for (String timeout : new String[] {"abc", "0", "-5"}) {
            Map<String, String> env = Map.of("LLM_PROVIDER", "ollama", "LLM_TIMEOUT_SECONDS", timeout);
            assertThrows(ConfigurationException.class, () -> AppConfig.load(noEnvFile(), env));
        }
    }

    @Test
    void providerIsCaseInsensitive() {
        assertEquals(LlmProvider.OLLAMA,
                AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "OlLaMa")).getProvider());
        assertEquals(LlmProvider.OPENAI,
                AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "OPENAI", "LLM_API_KEY", FAKE_KEY)).getProvider());
    }

    @Test
    void toStringNeverContainsKey() {
        AppConfig withKey = AppConfig.load(noEnvFile(), Map.of("LLM_API_KEY", FAKE_KEY));
        AppConfig withoutKey = AppConfig.load(noEnvFile(), Map.of("LLM_PROVIDER", "ollama"));

        assertFalse(withKey.toString().contains(FAKE_KEY));
        assertTrue(withKey.toString().contains("apiKey=<set>"));
        assertTrue(withoutKey.toString().contains("apiKey=<none>"));
    }

    @Test
    void exceptionMessagesNeverContainKey() {
        Map<String, Map<String, String>> invalidConfigs = Map.of(
                "unknown provider", Map.of("LLM_PROVIDER", "gemini", "LLM_API_KEY", FAKE_KEY),
                "bad timeout", Map.of("LLM_API_KEY", FAKE_KEY, "LLM_TIMEOUT_SECONDS", "never"));

        invalidConfigs.forEach((name, env) -> {
            ConfigurationException e = assertThrows(ConfigurationException.class,
                    () -> AppConfig.load(noEnvFile(), env), name);
            assertFalse(e.getMessage().contains(FAKE_KEY), name);
        });
    }
}
