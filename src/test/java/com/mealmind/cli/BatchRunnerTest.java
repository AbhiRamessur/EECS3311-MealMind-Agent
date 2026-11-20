package com.mealmind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.app.ApplicationContext;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private ApplicationContext ctx;
    private ByteArrayOutputStream buffer;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.create(dir, CLOCK);
        buffer = new ByteArrayOutputStream();
    }

    private int run(boolean continueOnError, String script) throws Exception {
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        return new BatchRunner(ctx, out, continueOnError).run(new BufferedReader(new StringReader(script)));
    }

    private List<String> lines() {
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void runsEveryCommandEchoedAfterThePrompt() throws Exception {
        assertEquals(BatchRunner.OK, run(false, "pantry add Rice 500 g\npantry list\n"));

        List<String> lines = lines();
        assertTrue(lines.contains("mealmind> pantry add Rice 500 g"), lines.toString());
        assertTrue(lines.indexOf("mealmind> pantry add Rice 500 g") < lines.indexOf("Added Rice. Pantry now has 500 g."));
        assertTrue(lines.contains("mealmind> pantry list"));
        assertEquals("Done: 2 commands, 0 failed.", lines.get(lines.size() - 1));
        assertTrue(ctx.pantry().getItem("Rice").isPresent());
    }

    @Test
    void blankLinesAndCommentsAreSkipped() throws Exception {
        assertEquals(BatchRunner.OK, run(false, "# a comment\n\n   \n  # indented comment\npantry add Rice 500 g\n"));

        assertFalse(buffer.toString(StandardCharsets.UTF_8).contains("mealmind> #"));
        assertEquals("Done: 1 command, 0 failed.", lines().get(lines().size() - 1));
    }

    @Test
    void theFirstFailureStopsTheScriptWithExitCodeFour() throws Exception {
        int status = run(false, "pantry add Rice 500 g\ndance\npantry add Beans 200 g\n");

        assertEquals(4, status);
        assertTrue(lines().contains("Unknown command: dance. Type 'help' for commands."));
        assertTrue(lines().contains("Stopped at line 2 after a failed command (use --continue-on-error to keep going)."));
        assertFalse(lines().contains("mealmind> pantry add Beans 200 g"));
        assertTrue(ctx.pantry().getItem("Beans").isEmpty());
    }

    @Test
    void continueOnErrorRunsEverythingAndStillReportsTheFailures() throws Exception {
        int status = run(true, "pantry add Rice 500 g\npantry add Rice 1 l\nrecipe show R999\npantry add Beans 200 g\n");

        assertEquals(4, status);
        assertTrue(lines().stream().anyMatch(l -> l.startsWith("Error: Cannot convert")), lines().toString());
        assertTrue(lines().stream().anyMatch(l -> l.startsWith("Error: No recipe with id 'R999'")), lines().toString());
        assertTrue(ctx.pantry().getItem("Beans").isPresent());
        assertEquals("Done: 4 commands, 2 failed.", lines().get(lines().size() - 1));
    }

    @Test
    void usageAndRefusalsCountAsFailures() throws Exception {
        assertEquals(4, run(true, "pantry add\nprofile diet vegetarian\nplan set 1 dinner R101\n"));

        assertTrue(lines().stream().anyMatch(l -> l.startsWith("Usage: pantry add")));
        assertTrue(lines().stream().anyMatch(l -> l.startsWith("Refused: ")));
        assertEquals("Done: 3 commands, 2 failed.", lines().get(lines().size() - 1));
    }

    @Test
    void exitEndsTheScriptEarly() throws Exception {
        assertEquals(BatchRunner.OK, run(false, "pantry add Rice 500 g\nexit\npantry add Beans 200 g\n"));

        assertTrue(lines().contains("mealmind> exit"));
        assertTrue(ctx.pantry().getItem("Beans").isEmpty());
        assertEquals("Done: 1 command, 0 failed.", lines().get(lines().size() - 1));
    }

    @Test
    void warningsAndInformationalLinesAreNotFailures() throws Exception {
        assertEquals(BatchRunner.OK, run(false, "undo\nrecipe pending\nplan nutrition\n"));

        assertTrue(lines().contains("Nothing to undo."));
    }
}
