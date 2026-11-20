package com.mealmind.cli;

import com.mealmind.app.ApplicationContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Runs CLI commands from a script (a file given with {@code --input}, or piped standard input) without
 * prompting: one command per line, blank lines and lines starting with {@code #} skipped, {@code exit} or
 * {@code quit} ending the script early. Each command is echoed after the {@code mealmind>} prompt before its
 * output, so a run reads like an interactive session. The commands run through the same {@link CLIHandler} as
 * an interactive session, so their output is identical.
 *
 * <p>A command <em>fails</em> when it prints a line starting with "Error: ", "Usage: ", "Unknown command: " or
 * "Refused: ". By default the script stops at the first failure; with {@code continueOnError} it keeps going.
 */
public final class BatchRunner {

    /** Every command succeeded. */
    public static final int OK = 0;
    /** At least one command failed (the script stopped there unless continuing on errors). */
    public static final int COMMAND_FAILED = 4;

    static final List<String> FAILURE_PREFIXES = List.of("Error: ", "Usage: ", "Unknown command: ", "Refused: ");
    private static final String PROMPT = "mealmind> ";

    private final PrintStream out;
    private final FailureWatch watch;
    private final CLIHandler handler;
    private final boolean continueOnError;

    /**
     * Creates a runner whose commands print to {@code out}.
     *
     * @param ctx             the session
     * @param out             where the commands and their output are printed
     * @param continueOnError whether to keep going after a failed command
     * @throws NullPointerException if an argument is null
     */
    public BatchRunner(ApplicationContext ctx, PrintStream out, boolean continueOnError) {
        this.out = Objects.requireNonNull(out, "out");
        this.watch = new FailureWatch(out);
        PrintStream watched = new PrintStream(watch, true, StandardCharsets.UTF_8);
        this.handler = new CLIHandler(Objects.requireNonNull(ctx, "ctx"), new BufferedReader(new StringReader("")), watched);
        this.continueOnError = continueOnError;
    }

    /**
     * Runs every command read from {@code script}.
     *
     * @param script the commands, one per line
     * @return {@link #OK} or {@link #COMMAND_FAILED}
     * @throws IOException if the script cannot be read
     */
    public int run(BufferedReader script) throws IOException {
        int lineNumber = 0;
        int commands = 0;
        int failed = 0;
        String line;
        while ((line = script.readLine()) != null) {
            lineNumber++;
            String command = line.strip();
            if (command.isEmpty() || command.startsWith("#")) {
                continue;
            }
            out.println(PROMPT + command);
            if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
                break;
            }
            commands++;
            watch.reset();
            handler.handleLine(command);
            out.flush();
            if (watch.failed()) {
                failed++;
                if (!continueOnError) {
                    out.println("Stopped at line " + lineNumber + " after a failed command"
                            + " (use --continue-on-error to keep going).");
                    return COMMAND_FAILED;
                }
            }
        }
        out.println("Done: " + commands + (commands == 1 ? " command, " : " commands, ") + failed + " failed.");
        return failed == 0 ? OK : COMMAND_FAILED;
    }

    /** Forwards every byte and notes whether a line began with a failure prefix since the last reset. */
    private static final class FailureWatch extends OutputStream {
        private final PrintStream target;
        private final StringBuilder lineStart = new StringBuilder();
        private boolean failed;

        FailureWatch(PrintStream target) {
            this.target = target;
        }

        void reset() {
            failed = false;
            lineStart.setLength(0);
        }

        boolean failed() {
            return failed;
        }

        @Override
        public void write(int b) {
            target.write(b);
            if (b == '\n') {
                lineStart.setLength(0);
            } else if (b != '\r' && lineStart.length() < 20) {
                lineStart.append(b < 128 ? (char) b : '?');
                String start = lineStart.toString();
                if (FAILURE_PREFIXES.stream().anyMatch(start::equals)) {
                    failed = true;
                }
            }
        }

        @Override
        public void flush() {
            target.flush();
        }
    }
}
