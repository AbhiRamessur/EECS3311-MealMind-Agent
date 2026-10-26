package com.mealmind.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a command line into words. Words are separated by whitespace; double-quoted text keeps its
 * spaces and loses the quotes, so {@code pantry add "Chicken Breast" 500 g} gives four words after
 * {@code pantry}. Quotes may also appear inside a word ({@code a"b c"d} is the single word {@code ab cd}).
 */
public final class CommandLineTokenizer {

    private CommandLineTokenizer() {
    }

    /**
     * Tokenizes one line.
     *
     * @param line the input line
     * @return the words, possibly empty
     * @throws IllegalArgumentException if a quote is not closed
     * @throws NullPointerException     if {@code line} is null
     */
    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean inToken = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                inToken = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quote");
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
