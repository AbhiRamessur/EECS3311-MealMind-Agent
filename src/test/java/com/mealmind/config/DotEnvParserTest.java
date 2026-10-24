package com.mealmind.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotEnvParserTest {

    @TempDir
    Path dir;

    private Map<String, String> parse(String content) throws IOException {
        Path file = dir.resolve(".env");
        Files.writeString(file, content);
        return DotEnvParser.parse(file);
    }

    @Test
    void ignoresCommentsAndBlankLines() throws IOException {
        Map<String, String> values = parse("# comment\n\n   \n  # indented comment\nLLM_MODEL=gpt-4o\n");

        assertEquals(Map.of("LLM_MODEL", "gpt-4o"), values);
    }

    @Test
    void trimsAndUnquotesValues() throws IOException {
        Map<String, String> values = parse("A = \"double\" \nB='single'\nC=\"unmatched'\n");

        assertEquals("double", values.get("A"));
        assertEquals("single", values.get("B"));
        assertEquals("\"unmatched'", values.get("C"));
    }

    @Test
    void keepsEqualsSignsInsideValue() throws IOException {
        Map<String, String> values = parse("OLLAMA_BASE_URL=http://host/path?a=1&b=2\n");

        assertEquals("http://host/path?a=1&b=2", values.get("OLLAMA_BASE_URL"));
    }

    @Test
    void missingFileGivesEmptyMap() {
        assertTrue(DotEnvParser.parse(dir.resolve("does-not-exist.env")).isEmpty());
    }

    @Test
    void ignoresMalformedLines() throws IOException {
        Map<String, String> values = parse("this line has no equals sign\nLLM_PROVIDER=ollama\n");

        assertEquals(Map.of("LLM_PROVIDER", "ollama"), values);
        assertFalse(values.containsKey("this line has no equals sign"));
    }
}
