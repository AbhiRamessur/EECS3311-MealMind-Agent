package com.mealmind.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mealmind.agent.PlanMeals;
import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.ScriptedAi;
import com.mealmind.model.MealType;
import com.mealmind.model.Unit;
import com.mealmind.profile.DietType;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real Plan, Shopping List and Nutrition tabs with a scripted model and the real background
 * runner. Skipped, never failed, when JavaFX cannot start.
 */
class GuiPlanSmokeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
    private static final long TIMEOUT_MS = 10_000;
    private static boolean toolkitAvailable;

    @TempDir
    Path dir;

    private ApplicationContext ctx;
    private ScriptedAi ai;
    private MainController controller;

    @BeforeAll
    static void startToolkit() {
        toolkitAvailable = GuiSelfTest.startToolkit().isEmpty();
        if (toolkitAvailable) {
            Platform.setImplicitExit(false);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit not available");
        ctx = ApplicationContext.create(dir, CLOCK, warning -> { });
        ctx.plan().setMeal(1, MealType.DINNER, PlanMeals.forRecipe(MealType.DINNER, ctx.recipeBook().get("R103").orElseThrow()));
        ai = new ScriptedAi();
        ctx.llm().useClient(ai);
        controller = onFx(() -> {
            MainController created = new MainController(ctx, null);
            created.planTab().confirmReplacementsWith(message -> true);
            return created;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (controller != null) {
            onFx(() -> {
                controller.close();
                return null;
            });
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Exception | AssertionError e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void waitFor(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(condition)) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for: " + what);
    }

    private PlanChatBar chat() {
        return controller.planTab().chat();
    }

    private void ask(String instruction) throws Exception {
        onFx(() -> {
            chat().instructionField().setText(instruction);
            chat().sendButton().fire();
            return null;
        });
    }

    private String dinner(int day) {
        return ctx.plan().getMeal(day, MealType.DINNER).map(m -> m.getRecipes().get(0).getId()).orElse("");
    }

    @Test
    void askThePlannerAppliesAnEditAndUndoRevertsIt() throws Exception {
        ai.reply("{\"edit\": {\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"R105\", \"explanation\": \"Quicker\"}}");

        ask("make monday dinner quicker");
        waitFor("the applied edit", () -> chat().resultLabel().getText().startsWith("Edit applied: "));

        assertEquals("R105", dinner(1));
        waitFor("the grid update", () -> controller.planTab().grid().cell(1, MealType.DINNER).getText().startsWith("Black Bean Tacos"));
        assertTrue(onFx(() -> chat().activity().model().lines().stream().anyMatch(l -> l.startsWith("Agent: 1 model calls, 0 tool calls; tokens: "))));
        onFx(() -> {
            controller.toolBar().undoButton().fire();
            return null;
        });
        assertEquals("R103", dinner(1));
    }

    @Test
    void clarifyLeavesThePlanUnchanged() throws Exception {
        ai.reply("{\"edit\": {\"action\": \"clarify\", \"question\": \"Which day do you mean?\"}}");

        ask("change dinner");
        waitFor("the question", () -> chat().resultLabel().getText().startsWith("Question: Which day do you mean?"));

        assertEquals("R103", dinner(1));
    }

    @Test
    void unsafeOnlyScriptIsRefused() throws Exception {
        ctx.profile().setDiet(DietType.VEGETARIAN);
        for (int i = 0; i < 4; i++) {
            ai.reply("{\"edit\": {\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"R101\"}}");
        }

        ask("chicken on monday");
        waitFor("the refusal", () -> chat().resultLabel().getText().startsWith("Refused: "));

        assertEquals("R103", dinner(1));
        assertTrue(onFx(() -> chat().resultLabel().getText().contains("Plan unchanged.")));
    }

    @Test
    void failingClientLeavesThePlanAndShowsTheError() throws Exception {
        ai.fail("The AI service had an internal error");

        ask("swap monday dinner");
        waitFor("the error", () -> controller.statusBar().getText().equals("The AI service had an internal error"));

        assertTrue(onFx(() -> controller.statusBar().getStyleClass().contains("status-error")));
        assertEquals("R103", dinner(1));
        assertTrue(onFx(() -> !chat().sendButton().isDisabled()));
    }

    @Test
    void explainWithAiShowsTheCommentaryAndKeepsTheNumbers() throws Exception {
        ai.reply("{\"commentary\": \"One dinner is planned; add lunches for more balanced days.\"}");
        NutritionTab nutrition = controller.nutritionTab();
        int lines = onFx(() -> nutrition.figures().getChildren().size());

        onFx(() -> {
            nutrition.explainButton().fire();
            return null;
        });
        waitFor("the commentary", () -> nutrition.commentary().getText().startsWith("AI commentary: One dinner is planned"));

        assertEquals(lines, (int) onFx(() -> nutrition.figures().getChildren().size()));
    }

    @Test
    void failingCommentaryShowsUnavailable() throws Exception {
        ai.fail("The AI service had an internal error");
        NutritionTab nutrition = controller.nutritionTab();

        onFx(() -> {
            nutrition.explainButton().fire();
            return null;
        });
        waitFor("the unavailable message", () -> nutrition.commentary().getText().startsWith("AI explanation unavailable: "));

        assertTrue(onFx(() -> controller.statusBar().getText().startsWith("AI explanation unavailable: ")));
        assertTrue(onFx(() -> !nutrition.figures().getChildren().isEmpty()));
    }

    @Test
    void disabledAiShowsVisibleLabels() throws Exception {
        ctx.llm().disable("Not configured");

        waitFor("the labels", () -> chat().unavailableLabel().isVisible() && controller.nutritionTab().unavailableLabel().isVisible());

        assertEquals("AI unavailable: Not configured", onFx(() -> chat().unavailableLabel().getText()));
        assertTrue(onFx(() -> chat().sendButton().isDisabled() && controller.nutritionTab().explainButton().isDisabled()));
        assertTrue(onFx(() -> controller.recipesTab().generate().unavailableLabel().isVisible()));
        assertTrue(onFx(() -> controller.recipesTab().recommend().unavailableLabel().isVisible()));
    }

    @Test
    void replacingThePlanRepointsTheBindings() throws Exception {
        onFx(() -> {
            PlanGenerateBar bar = controller.planTab().generateBar();
            bar.daysField().setText("2");
            bar.generateButton().fire();
            return null;
        });
        assertEquals(2, ctx.plan().getNumberOfDays());
        int rows = onFx(() -> controller.shoppingTab().table().getItems().size());
        String first = ctx.generator().getCurrent().getItems().get(0).getName();
        Unit unit = ctx.generator().getCurrent().getItems().get(0).getUnit();

        onFx(() -> {
            PantryTab pantry = controller.pantryTab();
            pantry.nameField().setText(first);
            pantry.quantityField().setText("100000");
            pantry.unitChoice().setValue(unit);
            pantry.addButton().fire();
            return null;
        });
        assertEquals(rows - 1, (int) onFx(() -> controller.shoppingTab().table().getItems().size()));

        ai.reply("{\"edit\": {\"action\": \"clear\", \"day\": 2, \"slot\": \"DINNER\"}}");
        ask("drop tuesday dinner");
        waitFor("the grid after the edit", () -> controller.planTab().grid().cell(2, MealType.DINNER).getText().equals("(empty)"));
    }
}
