package com.mealmind;

import com.mealmind.app.ApplicationContext;
import com.mealmind.app.Startup;
import com.mealmind.cli.CLIHandler;
import com.mealmind.gui.GuiSelfTest;
import com.mealmind.gui.MealMindApp;
import com.mealmind.persistence.RepositoryException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import javafx.application.Application;

/**
 * Entry point for MealMind. Starts the interactive CLI (default) or the JavaFX window ({@code --gui}) on the
 * same kind of session, and configures the AI provider from {@code .env} and the environment. If AI is not
 * configured, deterministic features still work. Startup never contacts the network.
 */
public final class Main {

    /** Application version; keep in sync with the version in {@code pom.xml}. */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private static final String USAGE = """
            Usage: mvn -q exec:java [-Dexec.args="--version|--help|--gui|--gui-selftest"]
              (no arguments)  start the interactive command line
              --gui           open the graphical interface (Profile and Pantry tabs)
              --gui-selftest  check the GUI wiring without showing a window (exit 0 ok, 1 failed, 3 skipped)
              --version       print the version
              --help          print this help
            Data is stored in the directory named by MEALMIND_DATA_DIR (default: ./data).
            AI settings are read from .env (see .env.example).""";

    private Main() {
    }

    /**
     * Runs MealMind and exits with the returned status if it is not 0.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int status = run(args, System.in, System.out, System.getenv());
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Runs MealMind reading AI settings from {@code ./.env}.
     *
     * @param args command-line arguments: none, {@code --version} or {@code --help}
     * @param in   where CLI commands are read from
     * @param out  where output is printed
     * @param env  environment variables
     * @return the exit status (see {@link #run(String[], InputStream, PrintStream, Map, Path)})
     */
    public static int run(String[] args, InputStream in, PrintStream out, Map<String, String> env) {
        return run(args, in, out, env, Path.of(".env"));
    }

    /**
     * Testable entry point.
     *
     * @param args    command-line arguments: none, {@code --version}, {@code --help}, {@code --gui} or
     *                {@code --gui-selftest}
     * @param in      where CLI commands are read from
     * @param out     where output is printed
     * @param env     environment variables ({@code MEALMIND_DATA_DIR} and the AI settings)
     * @param envFile the {@code .env} file to read AI settings from (it may not exist)
     * @return 0 on success, 1 if saved data could not be loaded (or the self-test failed), 2 for an unknown
     *         argument, 3 if the GUI self-test was skipped because JavaFX could not start
     */
    public static int run(String[] args, InputStream in, PrintStream out, Map<String, String> env, Path envFile) {
        if (args.length == 1 && args[0].equals("--version")) {
            out.println("MealMind " + VERSION);
            return 0;
        }
        if (args.length == 1 && args[0].equals("--help")) {
            out.println(USAGE);
            return 0;
        }
        if (args.length == 1 && args[0].equals("--gui-selftest")) {
            return GuiSelfTest.run(out, true);
        }
        boolean gui = args.length == 1 && args[0].equals("--gui");
        if (args.length > 0 && !gui) {
            out.println(USAGE);
            return 2;
        }

        ApplicationContext ctx;
        try {
            ctx = ApplicationContext.create(ApplicationContext.defaultDataDir(env), Clock.systemDefaultZone());
        } catch (RepositoryException e) {
            out.println("Error: " + e.getMessage());
            return 1;
        }
        Optional<String> aiProblem = Startup.configureAi(ctx, env, envFile);
        if (gui) {
            // Blocks until the window is closed. Main does not extend Application, so JavaFX on the class path works.
            MealMindApp.configure(ctx, aiProblem.orElse(null));
            Application.launch(MealMindApp.class);
            return 0;
        }
        out.println("Welcome to MealMind " + VERSION + ".");
        out.println("Type 'help' for commands.");
        aiProblem.ifPresent(out::println);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        new CLIHandler(ctx, reader, out).run();
        return 0;
    }
}
