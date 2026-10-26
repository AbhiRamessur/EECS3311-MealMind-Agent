package com.mealmind.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The arguments of one command, split into positional words and {@code --name value} flags.
 * Any problem throws {@link UsageException} with the command's usage text.
 */
record CliArgs(List<String> positional, Map<String, String> flags) {

    /** Splits {@code tokens}; flags must be in {@code allowedFlags}, have a value and appear once. */
    static CliArgs parse(List<String> tokens, Set<String> allowedFlags, String usage) {
        List<String> positional = new ArrayList<>();
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!token.startsWith("--")) {
                positional.add(token);
                continue;
            }
            String name = token.substring(2);
            if (!allowedFlags.contains(name) || i + 1 >= tokens.size() || flags.containsKey(name)) {
                throw new UsageException(usage);
            }
            flags.put(name, tokens.get(++i));
        }
        return new CliArgs(List.copyOf(positional), Map.copyOf(flags));
    }

    /** Throws {@link UsageException} unless there are between {@code min} and {@code max} positional words. */
    CliArgs expectPositional(int min, int max, String usage) {
        if (positional.size() < min || positional.size() > max) {
            throw new UsageException(usage);
        }
        return this;
    }

    /** Returns a required flag's value. */
    String requireFlag(String name, String usage) {
        String value = flags.get(name);
        if (value == null) {
            throw new UsageException(usage);
        }
        return value;
    }

    /** Parses a whole number, or throws {@link UsageException}. */
    static int parseInt(String text, String usage) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new UsageException(usage);
        }
    }

    /** Parses a decimal number, or throws {@link UsageException}. */
    static double parseDouble(String text, String usage) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new UsageException(usage);
        }
    }
}
