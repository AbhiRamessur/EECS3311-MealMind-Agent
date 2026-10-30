package com.mealmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Uses a nonexistent .env so tests never read the developer's real one. */
    private int run(String input, String... args) {
        return runWithEnv(Map.of("MEALMIND_DATA_DIR", dir.toString()), input, args);
    }

    private int runWithEnv(Map<String, String> env, String input, String... args) {
        return Main.run(args, input(input), out, env, dir.resolve("no-such.env"));
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void versionPrintsNameAndReturnsZero() {
        assertEquals(0, run("", "--version"));
        assertTrue(output().contains("MealMind " + Main.VERSION));
    }

    @Test
    void helpReturnsZero() {
        assertEquals(0, run("", "--help"));
        assertTrue(output().contains("Usage"));
    }

    @Test
    void unknownArgumentReturnsTwo() {
        assertEquals(2, run("", "--gui"));
        assertTrue(output().contains("Usage"));
    }

    @Test
    void scriptedSessionRunsAndSavesPantry() {
        assertEquals(0, run("pantry add Rice 500 g\nexit\n"));

        assertTrue(output().contains("Welcome to MealMind"));
        assertTrue(output().contains("Type 'help' for commands."));
        assertTrue(output().contains("Added Rice. Pantry now has 500 g."));
        assertTrue(Files.exists(dir.resolve("pantry.json")));
    }

    @Test
    void corruptPantryFileReturnsOne() throws Exception {
        Files.writeString(dir.resolve("pantry.json"), "not json");

        assertEquals(1, run("exit\n"));
        assertTrue(output().startsWith("Error: "));
    }

    @Test
    void missingKeyDisablesAiButCommandsStillWork() {
        assertEquals(0, run("llm status\npantry add Rice 500 g\nexit\n"));

        assertTrue(output().contains("AI features are disabled: No OpenAI API key configured"), output());
        assertTrue(output().contains("Deterministic features still work."));
        assertTrue(output().contains("Status: disabled (No OpenAI API key configured"));
        assertTrue(output().contains("Added Rice. Pantry now has 500 g."));
    }

    @Test
    void ollamaConfigEnablesAiWithoutContactingTheNetwork() {
        // Port 1 on loopback is closed: if startup tried to connect, nothing would answer. Only 'llm ping'
        // would make a call, and this session never sends it.
        Map<String, String> env = Map.of("MEALMIND_DATA_DIR", dir.toString(), "LLM_PROVIDER", "ollama",
                "OLLAMA_BASE_URL", "http://127.0.0.1:1");

        assertEquals(0, runWithEnv(env, "llm status\nexit\n"));

        assertFalse(output().contains("AI features are disabled"), output());
        assertTrue(output().contains("Provider: Ollama"));
        assertTrue(output().contains("Model: llama3"));
        assertTrue(output().contains("Status: available"));
        assertTrue(output().contains("AI calls: 0 ok, 0 failed"));
    }

    @Test
    void versionAndHelpNeverLoadConfig() {
        // An invalid provider would print a disabled line if configuration were loaded.
        Map<String, String> env = Map.of("MEALMIND_DATA_DIR", dir.toString(), "LLM_PROVIDER", "bogus");

        assertEquals(0, runWithEnv(env, "", "--version"));
        assertEquals(0, runWithEnv(env, "", "--help"));

        assertFalse(output().contains("disabled"), output());
        assertFalse(output().contains("Welcome"), output());
    }
}
