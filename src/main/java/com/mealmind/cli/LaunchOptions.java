package com.mealmind.cli;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The command-line options of {@code java -jar mealmind.jar} (and {@code mvn exec:java}). Option names ignore
 * case and work with one or two dashes ({@code -input} or {@code --input}).
 *
 * @param mode            what to run
 * @param input           the script file for {@code --input}, if given
 * @param continueOnError whether a script keeps going after a failed command
 * @param llm             the AI provider chosen for this run ({@code openai}, {@code ollama} or {@code none}),
 *                        or empty to use the configured one
 */
public record LaunchOptions(Mode mode, Optional<Path> input, boolean continueOnError, Optional<String> llm) {

    /** What the program does. */
    public enum Mode {
        /** The command line: interactive, or a script from {@code --input} or piped standard input. */
        CLI,
        /** The JavaFX window. */
        GUI,
        /** The GUI wiring check. */
        GUI_SELFTEST,
        /** Print the help. */
        HELP,
        /** Print the version. */
        VERSION
    }

    /** The accepted values of {@code --llm}. */
    public static final List<String> PROVIDERS = List.of("openai", "ollama", "none");

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public LaunchOptions {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(llm, "llm");
    }

    /**
     * Parses the arguments.
     *
     * @param args the command-line arguments
     * @return the options
     * @throws IllegalArgumentException with a user-readable message for an unknown, repeated or incomplete option
     */
    public static LaunchOptions parse(String[] args) {
        Objects.requireNonNull(args, "args");
        Mode mode = Mode.CLI;
        Path input = null;
        boolean continueOnError = false;
        String llm = null;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String raw = args[i];
            String name = optionName(raw);
            if (name == null) {
                throw new IllegalArgumentException("Unexpected argument '" + raw + "'");
            }
            if (!seen.add(canonical(name))) {
                throw new IllegalArgumentException("Option " + raw + " is given more than once");
            }
            switch (name) {
                case "input" -> {
                    if (i + 1 >= args.length || args[i + 1].isBlank()) {
                        throw new IllegalArgumentException("Option " + raw + " needs a file name");
                    }
                    input = Path.of(args[++i]);
                }
                case "llm" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Option " + raw + " needs one of: " + String.join(", ", PROVIDERS));
                    }
                    llm = provider(args[++i], raw);
                }
                case "llmapi" -> {
                    // The value is optional: "-LLMAPI -input task.txt" uses the configured provider.
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        llm = provider(args[++i], raw);
                    }
                }
                case "continue-on-error" -> continueOnError = true;
                case "gui" -> mode = exclusive(mode, Mode.GUI, raw);
                case "gui-selftest" -> mode = exclusive(mode, Mode.GUI_SELFTEST, raw);
                case "help", "h" -> mode = exclusive(mode, Mode.HELP, raw);
                case "version" -> mode = exclusive(mode, Mode.VERSION, raw);
                default -> throw new IllegalArgumentException("Unknown option '" + raw + "'");
            }
        }
        if (mode != Mode.CLI && (input != null || continueOnError || llm != null) && mode != Mode.GUI) {
            throw new IllegalArgumentException("--" + mode.name().toLowerCase(Locale.ROOT).replace('_', '-')
                    + " cannot be combined with other options");
        }
        if (mode == Mode.GUI && (input != null || continueOnError)) {
            throw new IllegalArgumentException("--gui cannot be combined with --input or --continue-on-error");
        }
        return new LaunchOptions(mode, Optional.ofNullable(input), continueOnError, Optional.ofNullable(llm));
    }

    /** "input" for "-input", "--INPUT" or "--input"; null when the argument is not an option. */
    private static String optionName(String arg) {
        if (arg.startsWith("--") && arg.length() > 2) {
            return arg.substring(2).toLowerCase(Locale.ROOT);
        }
        if (arg.startsWith("-") && arg.length() > 1 && !arg.startsWith("--")) {
            return arg.substring(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** -LLMAPI and --llm set the same thing, so giving both counts as a repeat. */
    private static String canonical(String name) {
        return name.equals("llmapi") ? "llm" : name.equals("h") ? "help" : name;
    }

    private static String provider(String value, String option) {
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(wanted)) {
            throw new IllegalArgumentException("Unknown AI provider '" + value + "' for " + option
                    + ". Use one of: " + String.join(", ", PROVIDERS));
        }
        return wanted;
    }

    private static Mode exclusive(Mode current, Mode wanted, String option) {
        if (current != Mode.CLI) {
            throw new IllegalArgumentException("Option " + option + " cannot be combined with --"
                    + current.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return wanted;
    }
}
