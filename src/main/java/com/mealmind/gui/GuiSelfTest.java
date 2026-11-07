package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.model.Unit;
import com.mealmind.profile.DietType;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.application.Platform;

/**
 * {@code Main --gui-selftest}: drives the real controls of a {@link MainController} (never shown) on the
 * FX thread against a temporary session, and checks that the models changed through them. Proves that the
 * GUI is wired to the same models, commands and history as the CLI.
 *
 * <p>Exit codes: {@link #OK}, {@link #FAILED}, or {@link #SKIPPED} when the JavaFX toolkit cannot start.
 */
public final class GuiSelfTest {

    /** All checks passed. */
    public static final int OK = 0;
    /** A check failed. */
    public static final int FAILED = 1;
    /** The JavaFX toolkit could not start (for example, no display). */
    public static final int SKIPPED = 3;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
    private static final Pattern KEY_LIKE = Pattern.compile("sk-[A-Za-z0-9_-]{8,}|LLM_API_KEY|Bearer");

    private GuiSelfTest() {
    }

    /**
     * Starts the JavaFX toolkit if it is not running yet.
     *
     * @return empty when the toolkit is running, otherwise why it could not start
     */
    public static Optional<String> startToolkit() {
        try {
            Platform.startup(() -> { });
            return Optional.empty();
        } catch (IllegalStateException alreadyRunning) {
            return Optional.empty();
        } catch (RuntimeException | Error e) {
            return Optional.of(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    /**
     * Runs the self-test.
     *
     * @param out         where one line per check is printed
     * @param exitToolkit {@code true} to shut the toolkit down afterwards (when run from {@code Main})
     * @return {@link #OK}, {@link #FAILED} or {@link #SKIPPED}
     */
    public static int run(PrintStream out, boolean exitToolkit) {
        Optional<String> problem = startToolkit();
        if (problem.isPresent()) {
            out.println("GUI self-test skipped: " + problem.get());
            return SKIPPED;
        }
        Path dir;
        try {
            dir = Files.createTempDirectory("mealmind-gui-selftest");
        } catch (IOException e) {
            out.println("GUI self-test FAILED: temporary directory (" + e.getMessage() + ")");
            return FAILED;
        }
        try {
            ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warning -> { });
            CompletableFuture<String> failed = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    failed.complete(checks(ctx, out));
                } catch (RuntimeException e) {
                    failed.complete("unexpected error " + e);
                }
            });
            String failure = failed.get(30, TimeUnit.SECONDS);
            if (failure != null) {
                out.println("GUI self-test FAILED: " + failure);
                return FAILED;
            }
            out.println("GUI self-test: ok");
            return OK;
        } catch (Exception e) {
            out.println("GUI self-test FAILED: " + e);
            return FAILED;
        } finally {
            delete(dir);
            if (exitToolkit) {
                Platform.exit();
            }
        }
    }

    /** Runs every check on the FX thread; returns the first failing check's name, or null. */
    private static String checks(ApplicationContext ctx, PrintStream out) {
        try (MainController controller = new MainController(ctx, null)) {
            ProfileTab profile = controller.profileTab();
            PantryTab pantry = controller.pantryTab();
            String failure = check(out, "profile tab applies diet and allergen", () -> {
                profile.dietChoice().setValue(DietType.VEGETARIAN);
                profile.allergenField().setText("peanut");
                profile.addAllergenButton().fire();
                profile.applyButton().fire();
                return ctx.profile().getDiet() == DietType.VEGETARIAN && ctx.profile().getAllergens().contains("peanut");
            });
            if (failure == null) {
                failure = check(out, "pantry tab adds an item through the history", () -> {
                    pantry.nameField().setText("Rice");
                    pantry.quantityField().setText("500");
                    pantry.unitChoice().setValue(Unit.GRAM);
                    pantry.addButton().fire();
                    return pantry.table().getItems().size() == 1 && ctx.pantry().getItem("Rice").isPresent()
                            && ctx.history().canUndo();
                });
            }
            if (failure == null) {
                failure = check(out, "undo button reverts the pantry add", () -> {
                    controller.toolBar().undoButton().fire();
                    return ctx.pantry().isEmpty() && pantry.table().getItems().isEmpty()
                            && !ctx.history().canUndo() && ctx.history().canRedo();
                });
            }
            if (failure == null) {
                failure = check(out, "AI status label shows no key", () -> {
                    String text = controller.aiStatusLabel().getText();
                    return text.startsWith("AI") && !KEY_LIKE.matcher(text).find();
                });
            }
            return failure;
        }
    }

    private static String check(PrintStream out, String name, BooleanSupplier test) {
        if (test.getAsBoolean()) {
            out.println("ok: " + name);
            return null;
        }
        return name;
    }

    private static void delete(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException ignored) {
            // A leftover temporary directory is harmless.
        }
    }
}
