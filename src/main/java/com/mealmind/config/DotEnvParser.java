package com.mealmind.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads simple {@code KEY=VALUE} files such as {@code .env}.
 *
 * <p>Rules: blank lines and lines starting with {@code #} are ignored; each line is split on the
 * first {@code =}; key and value are trimmed; one pair of matching surrounding single or double
 * quotes is removed from the value; lines without {@code =} are ignored.
 */
public final class DotEnvParser {

    private DotEnvParser() {
    }

    /**
     * Parses a {@code .env} file.
     *
     * @param file the file to read
     * @return the entries found, or an empty map if the file does not exist
     * @throws UncheckedIOException if the file exists but cannot be read
     */
    public static Map<String, String> parse(Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Map<String, String> entries = new HashMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = unquote(trimmed.substring(eq + 1).trim());
                entries.put(key, value);
            }
            return Map.copyOf(entries);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
