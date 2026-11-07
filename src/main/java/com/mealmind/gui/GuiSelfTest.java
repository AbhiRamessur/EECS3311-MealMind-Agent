package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.RecipeRow;
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
import java.util.List;
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
            return failure == null ? recipeChecks(ctx, controller, out) : failure;
        }
    }

    /**
     * Recipes tab checks (deterministic, no AI). Runs after the profile check, so the profile is vegetarian
     * with a peanut allergy: the meat and fish samples (R101, R104, R106) and the peanut toast (R109) are unsafe.
     */
    private static String recipeChecks(ApplicationContext ctx, MainController controller, PrintStream out) {
        RecipesTab recipes = controller.recipesTab();
        RecipeDetailsPane details = recipes.details();
        List<String> unsafe = List.of("R101", "R104", "R106", "R109");
        List<Check> checks = List.of(
                new Check("recipes table lists the 10 bundled recipes, unsafe ones flagged", () ->
                        recipes.table().getItems().size() == 10
                                && recipes.table().getItems().stream().filter(row -> !row.safe()).map(RecipeRow::id).toList().equals(unsafe)),
                new Check("filter 'lentil' shows only matching recipes", () -> {
                    recipes.filterField().setText("lentil");
                    boolean ok = ids(recipes).equals(List.of("R103"));
                    recipes.filterField().setText("");
                    return ok && recipes.table().getItems().size() == 10;
                }),
                new Check("selecting a recipe fills the details", () -> {
                    recipes.select("R103");
                    return details.titleLabel().getText().equals("R103 Red Lentil Soup")
                            && details.bodyArea().getText().contains("Profile check: OK");
                }),
                new Check("rating 4/5 updates the table and the preferences", () -> {
                    details.ratingChoice().setValue("4");
                    details.rateButton().fire();
                    return ctx.preferences().getRating("R103").orElse(0) == 4 && row(recipes, "R103").ratingText().equals("4/5");
                }),
                new Check("favorite toggle and 'Favorites only' filter", () -> {
                    details.favoriteButton().fire();
                    recipes.favoritesOnlyBox().fire();
                    boolean ok = ctx.preferences().isFavorite("R103") && ids(recipes).equals(List.of("R103"));
                    recipes.favoritesOnlyBox().fire();
                    return ok && recipes.table().getItems().size() == 10;
                }),
                new Check("scaling to 6 servings runs through the history and Undo restores it", () -> {
                    recipes.select("R103");
                    details.servingsField().setText("6");
                    details.scaleButton().fire();
                    boolean scaled = servings(ctx, "R103") == 6 && ctx.history().canUndo();
                    controller.toolBar().undoButton().fire();
                    return scaled && servings(ctx, "R103") == 4;
                }),
                new Check("remove is disabled for a bundled recipe", () -> {
                    recipes.select("R103");
                    return details.removeButton().isDisabled();
                }),
                new Check("recommend fills 5 safe rows; unsafe recipes stay listed but flagged", () -> {
                    recipes.recommend().recommendButton().fire();
                    List<String> recommended = recipes.recommend().table().getItems().stream().map(r -> r.id()).toList();
                    return recommended.size() == 5 && recommended.stream().noneMatch(unsafe::contains)
                            && recipes.table().getItems().size() == 10 && !row(recipes, "R101").safe();
                }),
                new Check("Generate is disabled with an AI tooltip when AI is not configured", () -> {
                    GeneratePane generate = recipes.generate();
                    return generate.generateButton().isDisabled() && generate.generateButton().getTooltip() != null
                            && generate.generateButton().getTooltip().getText().contains("AI features")
                            && generate.saveButton().isDisabled() && generate.discardButton().isDisabled();
                }));
        for (Check check : checks) {
            String failure = check(out, check.name(), check.test());
            if (failure != null) {
                return failure;
            }
        }
        return null;
    }

    /** One named self-test check. */
    private record Check(String name, BooleanSupplier test) {
    }

    private static List<String> ids(RecipesTab recipes) {
        return recipes.table().getItems().stream().map(RecipeRow::id).toList();
    }

    private static RecipeRow row(RecipesTab recipes, String id) {
        return recipes.table().getItems().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static int servings(ApplicationContext ctx, String id) {
        return ctx.recipeBook().get(id).orElseThrow().getServings();
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
