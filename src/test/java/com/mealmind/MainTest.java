package com.mealmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private int run(String input, String... args) {
        return Main.run(args, input(input), out, Map.of("MEALMIND_DATA_DIR", dir.toString()));
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
}
