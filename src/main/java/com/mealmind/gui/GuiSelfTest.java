package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.RecipeRow;
import com.mealmind.gui.logic.ShoppingRow;
import com.mealmind.gui.logic.TextExport;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.shopping.ShoppingItem;
import com.mealmind.shopping.ShoppingListFormatter;
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
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

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
                    failed.complete(checks(ctx, dir, out));
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
    private static String checks(ApplicationContext ctx, Path dir, PrintStream out) {
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
            if (failure == null) {
                failure = recipeChecks(ctx, controller, out);
            }
            return failure == null ? planChecks(ctx, controller, dir, out) : failure;
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
                            && generate.saveButton().isDisabled() && generate.discardButton().isDisabled()
                            && generate.unavailableLabel().isVisible()
                            && generate.unavailableLabel().getText().startsWith("AI unavailable: ");
                }));
        for (Check check : checks) {
            String failure = check(out, check.name(), check.test());
            if (failure != null) {
                return failure;
            }
        }
        return null;
    }

    /**
     * Plan, Shopping List and Nutrition checks (deterministic, no AI). The confirmation dialog for replacing a
     * plan is answered through {@link PlanTab#confirmReplacementsWith}, and the export is written to a file in the
     * temporary directory through {@link ShoppingTab#exportTo}, bypassing the file dialog.
     */
    private static String planChecks(ApplicationContext ctx, MainController controller, Path dir, PrintStream out) {
        PlanTab plan = controller.planTab();
        PlanGridView grid = plan.grid();
        PlanSlotEditor editor = plan.editor();
        ShoppingTab shopping = controller.shoppingTab();
        NutritionTab nutrition = controller.nutritionTab();
        int[] confirmations = {0};
        boolean[] answer = {true};
        plan.confirmReplacementsWith(message -> {
            confirmations[0]++;
            return answer[0];
        });
        List<Check> checks = List.of(
                new Check("plan tab shows 7 empty days", () -> grid.rowCount() == 7 && grid.filledCount() == 0),
                new Check("generating a 3-day budget plan fills lunch and dinner", () -> {
                    plan.generateBar().daysField().setText("3");
                    plan.generateBar().strategyChoice().getItems().stream().filter(o -> o.id().equals("budget"))
                            .findFirst().ifPresent(plan.generateBar().strategyChoice()::setValue);
                    plan.generateBar().generateButton().fire();
                    return ctx.plan().getNumberOfDays() == 3 && grid.rowCount() == 3 && grid.filledCount() == 6
                            && confirmations[0] == 0;
                }),
                new Check("replacing a plan with meals asks first (no keeps it, yes replaces it)", () -> {
                    MealPlan before = ctx.plan();
                    answer[0] = false;
                    plan.generateBar().generateButton().fire();
                    boolean kept = ctx.plan() == before && confirmations[0] == 1;
                    answer[0] = true;
                    plan.generateBar().generateButton().fire();
                    return kept && ctx.plan() != before && confirmations[0] == 2 && grid.filledCount() == 6;
                }),
                new Check("Clear slot empties a filled cell and Undo restores it", () -> {
                    grid.select(1, MealType.LUNCH);
                    editor.clearSlotButton().fire();
                    boolean cleared = ctx.plan().getMeal(1, MealType.LUNCH).isEmpty() && grid.filledCount() == 5;
                    controller.toolBar().undoButton().fire();
                    return cleared && ctx.plan().getMeal(1, MealType.LUNCH).isPresent() && grid.filledCount() == 6;
                }),
                new Check("Set meal puts R103 in Day 1 Breakfast and Undo reverts it", () -> {
                    grid.select(1, MealType.BREAKFAST);
                    editor.recipeChoice().setValue("R103");
                    editor.servingsField().setText("");
                    editor.setMealButton().fire();
                    boolean set = ctx.plan().getMeal(1, MealType.BREAKFAST).map(m -> m.getRecipes().get(0).getId())
                            .orElse("").equals("R103") && grid.cell(1, MealType.BREAKFAST).getText().startsWith("Red Lentil Soup");
                    controller.toolBar().undoButton().fire();
                    return set && ctx.plan().getMeal(1, MealType.BREAKFAST).isEmpty();
                }),
                new Check("a meat recipe is refused for the vegetarian profile", () -> {
                    grid.select(2, MealType.BREAKFAST);
                    editor.recipeChoice().setValue("R101");
                    editor.setMealButton().fire();
                    return ctx.plan().getMeal(2, MealType.BREAKFAST).isEmpty()
                            && editor.resultLabel().getText().startsWith("Refused: ");
                }),
                new Check("shopping list lists items and follows a pantry add", () -> {
                    List<ShoppingItem> items = ctx.generator().getCurrent().getItems();
                    if (items.isEmpty() || shopping.table().getItems().size() != items.size()) {
                        return false;
                    }
                    ShoppingItem first = items.get(0);
                    PantryTab pantry = controller.pantryTab();
                    pantry.nameField().setText(first.getName());
                    pantry.quantityField().setText("100000");
                    pantry.unitChoice().setValue(first.getUnit());
                    pantry.addButton().fire();
                    return shopping.table().getItems().size() == items.size() - 1
                            && shopping.table().getItems().stream().noneMatch(r -> r.name().equals(first.getName()));
                }),
                new Check("a checked row stays checked after a refresh", () -> {
                    ShoppingRow row = shopping.table().getItems().get(0);
                    shopping.toggle(row);
                    shopping.refresh();
                    return shopping.table().getItems().stream().filter(r -> r.key().equals(row.key())).allMatch(ShoppingRow::checked);
                }),
                new Check("export writes the shopping list text", () -> {
                    Path file = dir.resolve("export").resolve(TextExport.defaultFileName());
                    shopping.exportTo(file);
                    try {
                        return Files.readString(file).equals(ShoppingListFormatter.toText(ctx.generator().getCurrent()));
                    } catch (IOException e) {
                        return false;
                    }
                }),
                new Check("nutrition shows day lines and a calorie progress bar", () -> {
                    controller.profileTab().caloriesField().setText("2000");
                    controller.profileTab().applyButton().fire();
                    boolean dayLine = nutrition.figures().getChildren().stream()
                            .anyMatch(n -> n instanceof Label label && label.getText().startsWith("Day 1: "));
                    return dayLine && nutrition.bars().getChildren().size() == 1
                            && ((HBox) nutrition.bars().getChildren().get(0)).getChildren().stream()
                            .anyMatch(n -> n instanceof Label label && label.getText().startsWith("Calories: "));
                }),
                new Check("AI buttons on Plan and Nutrition are disabled with visible reasons", () ->
                        plan.chat().sendButton().isDisabled() && plan.chat().unavailableLabel().isVisible()
                                && plan.chat().unavailableLabel().getText().startsWith("AI unavailable: ")
                                && nutrition.explainButton().isDisabled() && nutrition.unavailableLabel().isVisible()));
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
