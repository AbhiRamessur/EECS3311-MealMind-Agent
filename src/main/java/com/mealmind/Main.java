package com.mealmind;

import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
import com.mealmind.persistence.RepositoryException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

/**
 * Entry point for MealMind. Starts the interactive CLI; the GUI and the AI agent are added in later
 * commits. LLM configuration ({@code AppConfig}) is not loaded yet.
 */
public final class Main {

    /** Application version; keep in sync with the version in {@code pom.xml}. */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private static final String USAGE = """
            Usage: mvn -q exec:java [-Dexec.args="--version|--help"]
              (no arguments)  start the interactive command line
              --version       print the version
              --help          print this help
            Data is stored in the directory named by MEALMIND_DATA_DIR (default: ./data).""";

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
     * Testable entry point.
     *
     * @param args command-line arguments: none, {@code --version} or {@code --help}
     * @param in   where CLI commands are read from
     * @param out  where output is printed
     * @param env  environment variables (for {@code MEALMIND_DATA_DIR})
     * @return 0 on success, 1 if saved data could not be loaded, 2 for an unknown argument
     */
    public static int run(String[] args, InputStream in, PrintStream out, Map<String, String> env) {
        if (args.length == 1 && args[0].equals("--version")) {
            out.println("MealMind " + VERSION);
            return 0;
        }
        if (args.length == 1 && args[0].equals("--help")) {
            out.println(USAGE);
            return 0;
        }
        if (args.length > 0) {
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
        out.println("Welcome to MealMind " + VERSION + ".");
        out.println("Type 'help' for commands.");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        new CLIHandler(ctx, reader, out).run();
        return 0;
    }
}
