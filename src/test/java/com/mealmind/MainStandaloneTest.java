package com.mealmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The standalone options: --input, piped standard input, --continue-on-error, --llm and --help. */
class MainStandaloneTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

    /** A nonexistent .env, so tests never read the developer's real one. */
    private int run(Map<String, String> extraEnv, String stdin, boolean terminal, String... args) {
        Map<String, String> env = new HashMap<>(extraEnv);
        env.put("MEALMIND_DATA_DIR", dir.resolve("data").toString());
        return Main.run(args, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)), out, env,
                dir.resolve("no-such.env"), terminal);
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Path script(String text) throws Exception {
        Path file = dir.resolve("task.txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void inputFileRunsEveryCommandAndExitsZero() throws Exception {
        Path file = script("# shopping\npantry add Rice 500 g\n\npantry list\n");

        assertEquals(0, run(Map.of(), "", true, "--input", file.toString(), "--llm", "none"));

        String output = output();
        assertTrue(output.contains("Running commands from " + file + "."), output);
        assertTrue(output.contains("mealmind> pantry add Rice 500 g"));
        assertTrue(output.contains("Added Rice. Pantry now has 500 g."));
        assertTrue(output.contains("Done: 2 commands, 0 failed."));
        assertFalse(output.contains("Type 'help' for commands."));
        assertTrue(Files.exists(dir.resolve("data").resolve("pantry.json")));
    }

    @Test
    void aFailedCommandGivesExitCodeFourUnlessContinuing() throws Exception {
        Path file = script("pantry add Rice 500 g\nrecipe show R999\npantry add Beans 200 g\n");

        assertEquals(4, run(Map.of(), "", true, "-input", file.toString(), "-llm", "none"));
        assertTrue(output().contains("Stopped at line 2"));
        assertFalse(output().contains("Added Beans"));

        buffer.reset();
        assertEquals(4, run(Map.of(), "", true, "--input", file.toString(), "--continue-on-error", "--llm", "none"));
        assertTrue(output().contains("Added Beans"));
        assertTrue(output().contains("Done: 3 commands, 1 failed."));
    }

    @Test
    void aMissingInputFileGivesExitCodeTwo() {
        assertEquals(2, run(Map.of(), "", true, "--input", dir.resolve("missing.txt").toString()));
        assertTrue(output().startsWith("Error: cannot read input file "), output());
    }

    @Test
    void pipedStandardInputRunsAsAScript() {
        assertEquals(0, run(Map.of(), "pantry add Rice 500 g\n# skipped\npantry list\n", false, "--llm", "none"));

        assertTrue(output().contains("Running commands from standard input."));
        assertTrue(output().contains("mealmind> pantry list"));
        assertTrue(output().contains("Done: 2 commands, 0 failed."));

        buffer.reset();
        assertEquals(4, run(Map.of(), "dance\n", false));
        assertTrue(output().contains("Unknown command: dance."));
    }

    @Test
    void aTerminalStaysInteractive() {
        assertEquals(0, run(Map.of(), "pantry add Rice 500 g\nexit\n", true));

        assertTrue(output().contains("Type 'help' for commands."));
        assertTrue(output().contains("mealmind> "));
        assertTrue(output().contains("Goodbye."));
        assertFalse(output().contains("Done:"));
    }

    @Test
    void llmChoosesOrTurnsOffTheProvider() {
        assertEquals(0, run(Map.of(), "llm status\n", false, "--llm", "none"));
        assertTrue(output().contains("AI features are disabled: turned off with --llm none. Deterministic features still work."));
        assertTrue(output().contains("Status: disabled (turned off with --llm none)"), output());

        buffer.reset();
        assertEquals(0, run(Map.of("LLM_PROVIDER", "openai"), "llm status\n", false, "-LLMAPI", "ollama"));
        assertTrue(output().contains("Provider: Ollama"), output());
        assertTrue(output().contains("Status: available"));

        buffer.reset();
        assertEquals(0, run(Map.of("LLM_PROVIDER", "ollama"), "llm status\n", false, "-LLMAPI"));
        assertTrue(output().contains("Provider: Ollama"), output());
    }

    @Test
    void openAiWithoutAKeyFallsBackToDeterministicFeatures() {
        assertEquals(0, run(Map.of(), "pantry add Rice 500 g\n", false, "--llm", "openai"));

        assertTrue(output().contains("AI features are disabled: No OpenAI API key configured"), output());
        assertTrue(output().contains("Added Rice."));
    }

    @Test
    void helpListsEveryOptionWithAnExampleAndNoKeyText() {
        assertEquals(0, run(Map.of(), "", true, "--help"));

        String help = output();
        for (String option : new String[] {"--input <file>", "--continue-on-error", "--llm <openai|ollama|none>",
                "-LLMAPI [provider]", "--gui ", "--gui-selftest", "--version", "--help"}) {
            assertTrue(help.contains(option), option);
        }
        assertTrue(help.contains("e.g. java -jar target/mealmind.jar --input examples/task-no-ai.txt --llm none"));
        assertTrue(help.contains("Exit codes: 0 ok"));
        assertFalse(Pattern.compile("LLM_API_KEY|\bsk-").matcher(help).find(), help);
    }

    @Test
    void invalidOptionsExplainTheProblemAndShowTheUsage() {
        assertEquals(2, run(Map.of(), "", true, "--llm", "gemini"));

        assertTrue(output().startsWith("Error: Unknown AI provider 'gemini' for --llm."), output());
        assertTrue(output().contains("Usage: java -jar target/mealmind.jar [options]"));
    }
}
