package com.mealmind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.cli.LaunchOptions.Mode;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LaunchOptionsTest {

    private static LaunchOptions parse(String... args) {
        return LaunchOptions.parse(args);
    }

    private static String error(String... args) {
        return assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(args)).getMessage();
    }

    @Test
    void noArgumentsMeansTheInteractiveCommandLine() {
        LaunchOptions options = parse();
        assertEquals(Mode.CLI, options.mode());
        assertEquals(Optional.empty(), options.input());
        assertFalse(options.continueOnError());
        assertEquals(Optional.empty(), options.llm());
    }

    @Test
    void inputAndContinueOnErrorWithOneOrTwoDashesAndAnyCase() {
        LaunchOptions two = parse("--input", "task.txt", "--continue-on-error");
        LaunchOptions one = parse("-INPUT", "task.txt", "-continue-on-error");

        assertEquals(Optional.of(Path.of("task.txt")), two.input());
        assertTrue(two.continueOnError());
        assertEquals(two, one);
    }

    @Test
    void llmChoosesTheProviderForThisRun() {
        assertEquals(Optional.of("ollama"), parse("--llm", "ollama").llm());
        assertEquals(Optional.of("openai"), parse("-llm", "OpenAI").llm());
        assertEquals(Optional.of("none"), parse("--llm", "none").llm());
        assertTrue(error("--llm", "gemini").startsWith("Unknown AI provider 'gemini' for --llm. Use one of: openai, ollama, none"));
        assertEquals("Option --llm needs one of: openai, ollama, none", error("--llm"));
    }

    @Test
    void llmapiTakesAnOptionalProvider() {
        LaunchOptions bare = parse("-LLMAPI", "-input", "task.txt");
        assertEquals(Optional.empty(), bare.llm());
        assertEquals(Optional.of(Path.of("task.txt")), bare.input());

        assertEquals(Optional.of("ollama"), parse("-LLMAPI", "ollama", "-input", "task.txt").llm());
        assertEquals(Optional.empty(), parse("--llmapi").llm());
        assertEquals("Option --llm is given more than once", error("-LLMAPI", "none", "--llm", "ollama"));
    }

    @Test
    void modesAndTheirConflicts() {
        assertEquals(Mode.HELP, parse("--help").mode());
        assertEquals(Mode.HELP, parse("-h").mode());
        assertEquals(Mode.VERSION, parse("-version").mode());
        assertEquals(Mode.GUI, parse("--gui").mode());
        assertEquals(Mode.GUI_SELFTEST, parse("--gui-selftest").mode());
        assertEquals(Optional.of("ollama"), parse("--gui", "--llm", "ollama").llm());

        assertEquals("Option --version cannot be combined with --help", error("--help", "--version"));
        assertEquals("--help cannot be combined with other options", error("--help", "--input", "x.txt"));
        assertEquals("--gui cannot be combined with --input or --continue-on-error", error("--gui", "--input", "x.txt"));
    }

    @Test
    void badArgumentsAreRejectedWithClearMessages() {
        assertEquals("Unknown option '--no-such-option'", error("--no-such-option"));
        assertEquals("Unexpected argument 'task.txt'", error("task.txt"));
        assertEquals("Option --input needs a file name", error("--input"));
        assertEquals("Option -input is given more than once", error("--input", "a.txt", "-input", "b.txt"));
    }
}
