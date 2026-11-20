package com.mealmind;

import com.mealmind.app.ApplicationContext;
import com.mealmind.app.Startup;
import com.mealmind.cli.BatchRunner;
import com.mealmind.cli.CLIHandler;
import com.mealmind.cli.LaunchOptions;
import com.mealmind.gui.GuiSelfTest;
import com.mealmind.gui.MealMindApp;
import com.mealmind.persistence.RepositoryException;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javafx.application.Application;

/**
 * Entry point for MealMind. Starts the command line (default) or the JavaFX window ({@code --gui}) on the
 * same kind of session, and configures the AI provider from {@code .env} and the environment. If AI is not
 * configured, deterministic features still work. Startup never contacts the network.
 *
 * <p>The command line is interactive at a terminal. With {@code --input <file>}, or when standard input is
 * piped, it runs the commands as a script ({@link BatchRunner}) and exits with 0 when every command succeeded.
 * The standalone jar ({@code java -jar target/mealmind.jar}) does not bundle JavaFX: the command line never
 * needs it, and {@code --gui} then explains how to start the window instead.
 */
public final class Main {

    /** Application version; keep in sync with the version in {@code pom.xml}. */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    /** A script command failed (see {@link BatchRunner#COMMAND_FAILED}). */
    public static final int EXIT_COMMAND_FAILED = BatchRunner.COMMAND_FAILED;

    private static final String JAR = "java -jar target/mealmind.jar";
    private static final String USAGE = """
            Usage: java -jar target/mealmind.jar [options]
                   (or with Maven: mvn -q exec:java "-Dexec.args=[options]")
              (no options)                start the interactive command line; piped standard input runs as a script
                                            e.g. %1$s
              --input <file>              run the commands in <file> (one per line) without prompting, then exit
                                            e.g. %1$s --input examples/task-no-ai.txt --llm none
              --continue-on-error         keep running a script after a failed command (default: stop there)
                                            e.g. %1$s --input examples/task.txt --continue-on-error
              --llm <openai|ollama|none>  choose the AI provider for this run; none turns the AI features off
                                            e.g. %1$s --llm ollama --input examples/task.txt
              -LLMAPI [provider]          same as --llm; without a provider, use the one configured in .env
                                            e.g. %1$s -LLMAPI -input examples/task.txt
              --gui                       open the graphical interface (needs JavaFX: start it with mvn javafx:run)
                                            e.g. mvn -q javafx:run
              --gui-selftest              check the GUI wiring without showing a window (exit 0 ok, 1 failed, 3 skipped)
                                            e.g. mvn -q javafx:run "-Dmealmind.javafxArgs=--gui-selftest"
              --version                   print the version
                                            e.g. %1$s --version
              --help                      print this help
                                            e.g. %1$s --help
            Options also work with one dash (-input, -llm, -help). In a script, blank lines and lines starting
            with # are skipped, and exit ends it early.
            Exit codes: 0 ok; 1 saved data could not be loaded or the GUI self-test failed; 2 invalid options
            or an unreadable input file; 3 GUI self-test skipped; 4 a script command failed.
            Data is stored in the directory named by MEALMIND_DATA_DIR (default: ./data).
            AI settings (LLM_PROVIDER, LLM_MODEL, OLLAMA_BASE_URL and the API key) come from the environment
            or .env (see .env.example); the key is never printed.""".formatted(JAR);

    private Main() {
    }

    /**
     * Runs MealMind and exits with the returned status if it is not 0.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int status = run(args, System.in, System.out, System.getenv(), Path.of(".env"), stdinIsTerminal());
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Runs MealMind reading AI settings from {@code ./.env}, with standard input treated as a terminal.
     *
     * @param args command-line arguments (see {@link #run(String[], InputStream, PrintStream, Map, Path, boolean)})
     * @param in   where CLI commands are read from
     * @param out  where output is printed
     * @param env  environment variables
     * @return the exit status
     */
    public static int run(String[] args, InputStream in, PrintStream out, Map<String, String> env) {
        return run(args, in, out, env, Path.of(".env"));
    }

    /**
     * Testable entry point, with standard input treated as a terminal (interactive).
     *
     * @param args    command-line arguments
     * @param in      where CLI commands are read from
     * @param out     where output is printed
     * @param env     environment variables ({@code MEALMIND_DATA_DIR} and the AI settings)
     * @param envFile the {@code .env} file to read AI settings from (it may not exist)
     * @return the exit status
     */
    public static int run(String[] args, InputStream in, PrintStream out, Map<String, String> env, Path envFile) {
        return run(args, in, out, env, envFile, true);
    }

    /**
     * Testable entry point.
     *
     * @param args            command-line arguments (see {@code --help})
     * @param in              where CLI commands are read from
     * @param out             where output is printed
     * @param env             environment variables ({@code MEALMIND_DATA_DIR} and the AI settings)
     * @param envFile         the {@code .env} file to read AI settings from (it may not exist)
     * @param stdinIsTerminal false when standard input is piped, so it runs as a script
     * @return 0 on success, 1 if saved data could not be loaded (or the self-test failed), 2 for invalid options or
     *         an unreadable input file, 3 if the GUI self-test was skipped, 4 if a script command failed
     */
    public static int run(String[] args, InputStream in, PrintStream out, Map<String, String> env, Path envFile,
                          boolean stdinIsTerminal) {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
            out.println(USAGE);
            return 2;
        }
        switch (options.mode()) {
            case VERSION -> {
                out.println("MealMind " + VERSION);
                return 0;
            }
            case HELP -> {
                out.println(USAGE);
                return 0;
            }
            case GUI_SELFTEST -> {
                return guiSelfTest(out);
            }
            default -> {
                // CLI and GUI continue below.
            }
        }
        if (options.input().isPresent() && !isReadableFile(options.input().get())) {
            out.println("Error: cannot read input file " + options.input().get());
            return 2;
        }

        ApplicationContext ctx;
        try {
            ctx = ApplicationContext.create(ApplicationContext.defaultDataDir(env), Clock.systemDefaultZone());
        } catch (RepositoryException e) {
            out.println("Error: " + e.getMessage());
            return 1;
        }
        Optional<String> aiProblem = configureAi(ctx, env, envFile, options.llm());
        if (options.mode() == LaunchOptions.Mode.GUI) {
            return gui(ctx, aiProblem, out);
        }
        out.println("Welcome to MealMind " + VERSION + ".");
        if (options.input().isPresent()) {
            return script(ctx, options, aiProblem, out);
        }
        if (!stdinIsTerminal) {
            aiProblem.ifPresent(out::println);
            out.println("Running commands from standard input.");
            return runScript(ctx, new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), options, out);
        }
        out.println("Type 'help' for commands.");
        aiProblem.ifPresent(out::println);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        new CLIHandler(ctx, reader, out).run();
        return 0;
    }

    /** Applies {@code --llm}: none turns AI off; openai or ollama overrides LLM_PROVIDER for this run only. */
    private static Optional<String> configureAi(ApplicationContext ctx, Map<String, String> env, Path envFile,
                                                Optional<String> llm) {
        if (llm.isPresent() && llm.get().equals("none")) {
            String reason = "turned off with --llm none";
            ctx.llm().disable(reason);
            return Optional.of("AI features are disabled: " + reason + ". Deterministic features still work.");
        }
        Map<String, String> effective = new HashMap<>(env);
        llm.ifPresent(provider -> effective.put("LLM_PROVIDER", provider));
        return Startup.configureAi(ctx, effective, envFile);
    }

    private static int script(ApplicationContext ctx, LaunchOptions options, Optional<String> aiProblem, PrintStream out) {
        Path file = options.input().orElseThrow();
        aiProblem.ifPresent(out::println);
        out.println("Running commands from " + file + ".");
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return runScript(ctx, reader, options, out);
        } catch (IOException e) {
            out.println("Error: cannot read input file " + file + ": " + e.getMessage());
            return 2;
        }
    }

    private static boolean isReadableFile(Path file) {
        return Files.isRegularFile(file) && Files.isReadable(file);
    }

    private static int runScript(ApplicationContext ctx, BufferedReader reader, LaunchOptions options, PrintStream out) {
        try {
            return new BatchRunner(ctx, out, options.continueOnError()).run(reader);
        } catch (IOException e) {
            out.println("Error: could not read input: " + e.getMessage());
            return 2;
        }
    }

    private static int gui(ApplicationContext ctx, Optional<String> aiProblem, PrintStream out) {
        try {
            // Blocks until the window is closed. Main does not extend Application, so JavaFX on the class path works.
            MealMindApp.configure(ctx, aiProblem.orElse(null));
            Application.launch(MealMindApp.class);
            return 0;
        } catch (NoClassDefFoundError e) {
            out.println("Error: the graphical interface needs JavaFX, which the standalone jar does not include."
                    + " Start it with: mvn -q javafx:run");
            return 1;
        }
    }

    private static int guiSelfTest(PrintStream out) {
        try {
            return GuiSelfTest.run(out, true);
        } catch (NoClassDefFoundError e) {
            out.println("GUI self-test skipped: JavaFX is not on the class path (the standalone jar does not include it;"
                    + " use mvn -q javafx:run \"-Dmealmind.javafxArgs=--gui-selftest\")");
            return GuiSelfTest.SKIPPED;
        }
    }

    /**
     * Returns whether standard input is an interactive terminal. On Java 21 {@link System#console()} is null when
     * input or output is redirected; newer runtimes report it through {@code Console.isTerminal()}.
     */
    private static boolean stdinIsTerminal() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            Method isTerminal = Console.class.getMethod("isTerminal");
            return (Boolean) isTerminal.invoke(console);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
