package com.mealmind.gui;

import static com.mealmind.gui.logic.SubstituteReplies.CHICKPEAS;
import static com.mealmind.gui.logic.SubstituteReplies.PEANUT_BUTTER;
import static com.mealmind.gui.logic.SubstituteReplies.THIGH;
import static com.mealmind.gui.logic.SubstituteReplies.TOFU;
import static com.mealmind.gui.logic.SubstituteReplies.reply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mealmind.app.ApplicationContext;
import com.mealmind.command.ScaleRecipeCommand;
import com.mealmind.gui.logic.ScriptedAi;
import com.mealmind.gui.logic.SubstituteRow;
import com.mealmind.llm.LLMClient;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.TokenUsage;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import com.mealmind.profile.DietType;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the Recipes tab's "Substitute an ingredient" section with the real background runner and a scripted
 * model. Skipped, never failed, when JavaFX cannot start.
 */
class GuiSubstituteSmokeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private static final long TIMEOUT_MS = 10_000;
    private static final String NO_SAFE = "No safe substitute for Chicken breast was found. Consider replacing the whole recipe; "
            + "try 'recipe recommend' for alternatives.";
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

    /** Polls {@code condition} on the FX thread; the condition must not call {@link #onFx} itself. */
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

    private SubstitutePane pane() {
        return controller.recipesTab().details().substitute();
    }

    private void run(Runnable action) throws Exception {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    /** Selects {@code id}, asks for substitutes for its first ingredient and waits until the job is over. */
    private void suggest(String id) throws Exception {
        run(() -> {
            controller.recipesTab().select(id);
            pane().suggestButton().fire();
        });
        waitFor("the suggestion", () -> !controller.statusBar().getText().isEmpty() && !pane().suggestButton().isDisabled());
    }

    private List<SubstituteRow> rows() throws Exception {
        return onFx(() -> List.copyOf(pane().table().getItems()));
    }

    private String status() throws Exception {
        return onFx(() -> controller.statusBar().getText());
    }

    private Recipe r101() {
        return ctx.recipeBook().get("R101").orElseThrow();
    }

    private void applyRow(int index) throws Exception {
        run(() -> {
            pane().table().getSelectionModel().select(index);
            pane().applyButton().fire();
        });
    }

    @Test
    void suggestShowsTheOptionsAndChangesNothing() throws Exception {
        ctx.pantry().addItem(new PantryItem("Firm tofu", 400, Unit.GRAM));
        ai.reply(reply(TOFU, CHICKPEAS));
        Recipe before = r101();

        suggest("R101");

        List<SubstituteRow> rows = rows();
        assertEquals(2, rows.size());
        assertEquals(new SubstituteRow("1", "300 g Firm tofu (x1)", "Absorbs the sauce", "in pantry",
                "about 450 kcal, P 48.0 g, C 6.0 g, F 27.0 g (AI estimates)"), rows.get(0));
        assertEquals(new SubstituteRow("2", "450 g Chickpeas (x1.5)", "", "",
                "about 540 kcal, P 30.0 g, C 90.0 g, F 9.0 g (AI estimates)"), rows.get(1));
        assertEquals("Substitutes for Chicken breast in R101 Chicken Stir-Fry (reason: preference):",
                onFx(() -> pane().headerLabel().getText()));
        assertTrue(status().contains("Agent: 1 model calls, 0 tool calls;"), status());
        assertSame(before, r101());
        assertTrue(onFx(() -> pane().applyButton().isDisabled() && !pane().discardButton().isDisabled()));
    }

    @Test
    void applyReplacesTheIngredientAndOneUndoRestores() throws Exception {
        ai.reply(reply(TOFU, CHICKPEAS));
        Recipe before = r101();
        suggest("R101");

        applyRow(0);

        assertEquals("Firm tofu", r101().getIngredients().get(0).getName());
        waitFor("the details", () -> controller.recipesTab().details().bodyArea().getText().contains("Firm tofu"));
        assertTrue(status().startsWith("Substituted: Substituted Chicken breast with Firm tofu in Chicken Stir-Fry"), status());
        assertTrue(status().contains("Per serving: 707 -> 685 kcal"), status());
        assertTrue(status().contains("Note: R101 is a bundled sample recipe; this change lasts for this session only."), status());
        assertTrue(status().endsWith("Use 'undo' to revert."), status());
        assertTrue(rows().isEmpty());
        assertFalse(onFx(() -> pane().session().isPending()));

        run(() -> controller.toolBar().undoButton().fire());
        assertSame(before, r101());
    }

    @Test
    void discardClearsTheRows() throws Exception {
        ai.reply(reply(TOFU));
        suggest("R101");

        run(() -> pane().discardButton().fire());

        assertTrue(rows().isEmpty());
        assertEquals("Discarded the pending substitution.", status());
        assertTrue(onFx(() -> pane().discardButton().isDisabled()));
    }

    @Test
    void unsafeOnlyAnswersShowTheNoSafeSubstituteMessage() throws Exception {
        ctx.profile().setDiet(DietType.VEGETARIAN);
        ai.reply(reply(THIGH), reply(THIGH), reply(THIGH), reply(THIGH));

        suggest("R101");

        assertEquals(NO_SAFE, onFx(() -> pane().messageLabel().getText()));
        assertTrue(rows().isEmpty());
        assertFalse(onFx(() -> pane().session().isPending()));
        assertTrue(status().contains("Agent: 4 model calls"), status());
    }

    @Test
    void profileChangeAfterTheSuggestionRefusesApplyAndKeepsIt() throws Exception {
        ai.reply(reply(PEANUT_BUTTER, TOFU));
        Recipe before = r101();
        suggest("R101");
        ctx.profile().addAllergen("peanut");

        applyRow(0);

        assertTrue(status().startsWith("Refused: "), status());
        assertTrue(onFx(() -> controller.statusBar().getStyleClass().contains("status-error")));
        assertTrue(onFx(() -> pane().session().isPending()));
        assertEquals(2, rows().size());
        assertSame(before, r101());
    }

    @Test
    void recipeChangedBetweenSuggestAndApplyIsRefusedAndCleared() throws Exception {
        ai.reply(reply(TOFU));
        suggest("R101");
        run(() -> ctx.history().pushAndExecute(new ScaleRecipeCommand(ctx.recipeBook(), "R101", 4)));

        applyRow(0);

        assertEquals("The recipe changed since this suggestion was made. Run 'recipe substitute' again.", status());
        assertFalse(onFx(() -> pane().session().isPending()));
        assertTrue(rows().isEmpty());
        assertEquals("Chicken breast", r101().getIngredients().get(0).getName());
    }

    @Test
    void selectingAnotherRecipeClearsTheSuggestion() throws Exception {
        ai.reply(reply(TOFU));
        suggest("R101");

        run(() -> controller.recipesTab().select("R103"));

        assertFalse(onFx(() -> pane().session().isPending()));
        assertTrue(rows().isEmpty());
        assertEquals("Red lentils", onFx(() -> pane().ingredientChoice().getValue()));
    }

    @Test
    void aFailingClientKeepsTheEarlierSuggestion() throws Exception {
        ai.reply(reply(TOFU)).fail("The AI service had an internal error");
        Recipe before = r101();
        suggest("R101");

        run(() -> pane().suggestButton().fire());
        waitFor("the error", () -> controller.statusBar().getText().equals("The AI service had an internal error"));

        assertTrue(onFx(() -> controller.statusBar().getStyleClass().contains("status-error")));
        assertTrue(onFx(() -> pane().session().isPending()));
        assertEquals(1, rows().size());
        assertSame(before, r101());
    }

    @Test
    void theSharedFlagDisablesOtherAiControlsWhileSuggesting() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        ctx.llm().useClient(new LLMClient() {
            @Override
            public String providerName() {
                return "Blocking";
            }

            @Override
            public String modelName() {
                return "blocking-model";
            }

            @Override
            public LlmResponse complete(LlmRequest request) {
                entered.countDown();
                try {
                    if (!release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        throw new IllegalStateException("Test latch was never released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return new LlmResponse(reply(TOFU), new TokenUsage(100, 20), "blocking-model");
            }
        });
        ctx.pantry().addItem(new PantryItem("Red lentils", 400, Unit.GRAM, LocalDate.of(2026, 11, 10)));
        waitFor("AI enabled", () -> !pane().unavailableLabel().isVisible());
        run(() -> {
            controller.recipesTab().select("R101");
            pane().suggestButton().fire();
        });
        assertTrue(entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        assertTrue(onFx(() -> pane().suggestButton().isDisabled() && pane().applyButton().isDisabled()
                && controller.recipesTab().generate().generateButton().isDisabled()
                && controller.planTab().chat().sendButton().isDisabled()
                && controller.nutritionTab().explainButton().isDisabled()
                && controller.pantryTab().leftovers().findButton().isDisabled()));

        release.countDown();
        waitFor("the job to finish", () -> !pane().suggestButton().isDisabled() && pane().table().getItems().size() == 1);
        assertFalse(onFx(() -> controller.planTab().chat().sendButton().isDisabled()));
    }

    @Test
    void suggestIsDisabledWithAVisibleReasonWhenAiIsOff() throws Exception {
        ctx.llm().disable("Not configured");
        run(() -> controller.recipesTab().select("R101"));

        waitFor("the label", () -> pane().unavailableLabel().isVisible());

        assertEquals("AI unavailable: Not configured", onFx(() -> pane().unavailableLabel().getText()));
        assertTrue(onFx(() -> pane().suggestButton().isDisabled()));
    }
}
