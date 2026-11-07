package com.mealmind.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.ScriptedAi;
import com.mealmind.profile.DietType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
 * Drives the real Recipes tab with a scripted model and the real {@code BackgroundRunner} (results arrive
 * through the FX thread, so the test waits for them). Skipped, never failed, when JavaFX cannot start.
 */
class GuiRecipesSmokeTest {

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
        ai = new ScriptedAi();
        ctx.llm().useClient(ai);
        controller = onFx(() -> new MainController(ctx, null));
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

    /** Polls {@code condition} on the FX thread until it holds; fails clearly on timeout. */
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

    private RecipesTab recipes() {
        return controller.recipesTab();
    }

    private void generate(String prompt) throws Exception {
        onFx(() -> {
            recipes().generate().promptField().setText(prompt);
            recipes().generate().generateButton().fire();
            return null;
        });
    }

    @Test
    void generateSaveAndUndo() throws Exception {
        ai.reply(ScriptedAi.recipe("Chickpea Pasta", "Chickpea pasta", "Spinach"));

        generate("high protein pasta");
        waitFor("the preview", () -> recipes().generate().titleLabel().getText().equals("Generated recipe AI001: Chickpea Pasta"));

        List<String> log = onFx(() -> recipes().generate().activity().model().lines());
        assertTrue(log.stream().anyMatch(line -> line.startsWith("[agent] ")), log.toString());
        assertTrue(log.get(log.size() - 1).startsWith("Agent: 1 model calls, 0 tool calls, 0 repairs, 0 safety retries"), log.toString());
        assertEquals(10, ctx.recipeBook().size());
        assertEquals("Pending recipe AI001 (not saved)", onFx(() -> controller.statusBar().getText()));
        assertFalse(onFx(() -> recipes().generate().saveButton().isDisabled()));

        onFx(() -> {
            recipes().generate().saveButton().fire();
            return null;
        });
        assertEquals(11, (int) onFx(() -> recipes().table().getItems().size()));
        assertTrue(ctx.recipeBook().contains("AI001"));
        assertTrue(Files.exists(ctx.userRecipesFile()));
        assertEquals("Saved Chickpea Pasta as AI001. Use Undo to remove it.", onFx(() -> controller.statusBar().getText()));

        onFx(() -> {
            controller.toolBar().undoButton().fire();
            return null;
        });
        assertEquals(10, (int) onFx(() -> recipes().table().getItems().size()));
    }

    @Test
    void unsafeCandidateShowsTheWarningAndCannotBeSaved() throws Exception {
        ctx.profile().setDiet(DietType.VEGETARIAN);
        for (int i = 0; i < 4; i++) {
            ai.reply(ScriptedAi.recipe("Chicken Pasta", "Chicken breast", "Pasta"));
        }

        generate("chicken pasta");
        waitFor("the unsafe preview", () -> recipes().generate().titleLabel().getText().startsWith("Generated recipe AI001"));

        assertTrue(onFx(() -> recipes().generate().banner().getStyleClass().contains("banner-warning")));
        assertTrue(onFx(() -> recipes().generate().banner().getText().startsWith("SAFETY WARNING")));
        assertTrue(onFx(() -> recipes().generate().saveButton().isDisabled()));
        assertFalse(onFx(() -> recipes().generate().discardButton().isDisabled()));
    }

    @Test
    void failingModelLeavesThePendingRecipeAndShowsTheError() throws Exception {
        ai.reply(ScriptedAi.recipe("Chickpea Pasta", "Chickpea pasta", "Spinach")).fail("The AI service had an internal error");
        generate("pasta");
        waitFor("the first preview", () -> recipes().generate().titleLabel().getText().endsWith("Chickpea Pasta"));

        generate("another one");
        waitFor("the error", () -> controller.statusBar().getText().equals("The AI service had an internal error"));

        assertTrue(onFx(() -> controller.statusBar().getStyleClass().contains("status-error")));
        assertEquals("Chickpea Pasta", ctx.agent().pendingRecipe().orElseThrow().recipe().getName());
        assertFalse(onFx(() -> recipes().generate().generateButton().isDisabled()));
    }

    @Test
    void explainWithAiReordersTheRecommendations() throws Exception {
        ai.reply("{\"ranking\": [{\"id\": \"R103\", \"reason\": \"Warm and filling\"}]}");

        onFx(() -> {
            recipes().recommend().explainBox().setSelected(true);
            recipes().recommend().recommendButton().fire();
            return null;
        });
        waitFor("the AI-ranked list", () -> recipes().recommend().heading().getText().equals("AI-ranked"));

        assertEquals("R103", onFx(() -> recipes().recommend().table().getItems().get(0).id()));
        assertEquals("Warm and filling", onFx(() -> recipes().recommend().table().getItems().get(0).reason()));
        assertEquals(5, (int) onFx(() -> recipes().recommend().table().getItems().size()));
    }

    @Test
    void failingReRankKeepsTheDeterministicList() throws Exception {
        ai.fail("The AI service had an internal error");

        onFx(() -> {
            recipes().recommend().explainBox().setSelected(true);
            recipes().recommend().recommendButton().fire();
            return null;
        });
        waitFor("the unavailable message", () -> controller.statusBar().getText().startsWith("AI explanation unavailable: "));

        assertEquals("Recommendations", onFx(() -> recipes().recommend().heading().getText()));
        assertEquals("R101", onFx(() -> recipes().recommend().table().getItems().get(0).id()));
        assertEquals(5, (int) onFx(() -> recipes().recommend().table().getItems().size()));
    }
}
