package com.mealmind.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private ApplicationContext context() {
        return ApplicationContext.create(dir.resolve("data"), CLOCK, warning -> { });
    }

    private Path noEnvFile() {
        return dir.resolve("no-such.env");
    }

    @Test
    void openAiWithoutKeyDisablesAiWithTheCliMessage() {
        ApplicationContext ctx = context();

        Optional<String> note = Startup.configureAi(ctx, Map.of(), noEnvFile());

        assertTrue(note.orElseThrow().startsWith("AI features are disabled: No OpenAI API key configured"), note.get());
        assertTrue(note.get().endsWith(". Deterministic features still work."), note.get());
        assertFalse(ctx.llm().isAvailable());
        assertTrue(ctx.llm().unavailableReason().orElseThrow().startsWith("No OpenAI API key configured"));
    }

    @Test
    void ollamaIsConfiguredWithoutAnyNetworkCall() {
        ApplicationContext ctx = context();

        // Port 1 on loopback is closed: any connection attempt would fail, so success proves none was made.
        Optional<String> note = Startup.configureAi(ctx,
                Map.of("LLM_PROVIDER", "ollama", "OLLAMA_BASE_URL", "http://127.0.0.1:1"), noEnvFile());

        assertEquals(Optional.empty(), note);
        assertTrue(ctx.llm().isAvailable());
        assertEquals(Optional.of("Ollama"), ctx.llm().providerName());
        assertEquals(0, ctx.llm().usage().snapshot().successfulCalls() + ctx.llm().usage().snapshot().failedCalls());
    }

    @Test
    void invalidConfigurationDisablesAiInTheSameStyle() {
        ApplicationContext ctx = context();

        Optional<String> note = Startup.configureAi(ctx,
                Map.of("LLM_PROVIDER", "ollama", "OLLAMA_BASE_URL", "not a url"), noEnvFile());

        assertTrue(note.orElseThrow().startsWith("AI features are disabled: "), note.get());
        assertTrue(note.get().endsWith(". Deterministic features still work."), note.get());
        assertFalse(note.get().contains(".. "), note.get());
        assertFalse(ctx.llm().isAvailable());
    }
}
