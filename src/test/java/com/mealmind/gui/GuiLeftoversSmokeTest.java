package com.mealmind.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mealmind.agent.PlanMeals;
import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.LeftoverRow;
import com.mealmind.gui.logic.ScriptedAi;
import com.mealmind.llm.LLMClient;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.TokenUsage;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
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
 * Drives the Pantry tab's "Use up expiring items" section with the real background runner and a scripted model.
 * Skipped, never failed, when JavaFX cannot start.
 */
class GuiLeftoversSmokeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 10);
    private static final long TIMEOUT_MS = 10_000;
    private static final String RANKING = "{\"ranking\": [{\"id\": \"R103\", \"reason\": \"Warm soup first\"}, "
            + "{\"id\": \"R107\", \"reason\": \"Quick omelette\"}]}";
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

    private LeftoversPane pane() {
        return controller.pantryTab().leftovers();
    }

    private void stock(String name, LocalDate expiry) throws Exception {
        onFx(() -> ctx.pantry().addItem(new PantryItem(name, 400, Unit.GRAM, expiry)));
    }

    private void find() throws Exception {
        onFx(() -> {
            pane().findButton().fire();
            return null;
        });
    }

    private List<String> recipes() throws Exception {
        return onFx(() -> pane().table().getItems().stream().map(LeftoverRow::recipeText).toList());
    }

    private String status() throws Exception {
        return onFx(() -> controller.statusBar().getText());
    }

    @Test
    void findShowsRowsAndLeavesThePlanUnchanged() throws Exception {
        stock("Red lentils", TOMORROW);

        find();

        assertEquals(List.of("Red Lentil Soup"), recipes());
        assertEquals("Day 1 (Monday) BREAKFAST", onFx(() -> pane().table().getItems().get(0).slotText()));
        assertEquals("Expiring within 3 days: Red lentils (expires in 1 day)", onFx(() -> pane().expiringLabel().getText()));
        assertEquals(28, ctx.plan().getEmptySlots().size());
        assertTrue(onFx(() -> !pane().applyButton().isDisabled()));
    }

    @Test
    void applyChangesThePlanGridAndShoppingListAndOneUndoReverts() throws Exception {
        stock("Red lentils", TOMORROW);
        find();

        onFx(() -> {
            pane().applyButton().fire();
            return null;
        });

        assertEquals("R103", ctx.plan().getMeal(1, MealType.BREAKFAST).orElseThrow().getRecipes().get(0).getId());
        waitFor("the grid cell", () -> controller.planTab().grid().cell(1, MealType.BREAKFAST).getText().startsWith("Red Lentil Soup"));
        waitFor("the shopping list", () -> controller.shoppingTab().table().getItems().stream().anyMatch(r -> r.name().equals("Onion")));
        assertTrue(status().startsWith("Applied: Add 1 leftover meal(s) to the plan."), status());
        assertTrue(recipes().isEmpty());

        onFx(() -> {
            controller.toolBar().undoButton().fire();
            return null;
        });
        assertEquals(28, ctx.plan().getEmptySlots().size());
        waitFor("the empty shopping list", () -> controller.shoppingTab().table().getItems().isEmpty());
    }

    @Test
    void discardClearsTheProposal() throws Exception {
        stock("Red lentils", TOMORROW);
        find();

        onFx(() -> {
            pane().discardButton().fire();
            return null;
        });

        assertTrue(recipes().isEmpty());
        assertEquals("Discarded the leftover proposal.", status());
        assertTrue(onFx(() -> pane().applyButton().isDisabled() && pane().discardButton().isDisabled()));
    }

    @Test
    void blockedMessagesAndOverwrite() throws Exception {
        find();
        assertEquals("The pantry is empty; nothing to do.", onFx(() -> pane().messageLabel().getText()));

        stock("Rice", null);
        find();
        assertEquals("No pantry items expire within 3 days; nothing to do.", onFx(() -> pane().messageLabel().getText()));

        stock("Red lentils", TOMORROW);
        onFx(() -> {
            MealPlan plan = ctx.newPlan(1);
            for (MealType type : MealType.values()) {
                plan.setMeal(1, type, PlanMeals.forRecipe(type, ctx.recipeBook().get("R102").orElseThrow()));
            }
            return null;
        });
        find();
        assertTrue(onFx(() -> pane().messageLabel().getText()).startsWith("The plan has no empty slots."));
        assertTrue(recipes().isEmpty());

        onFx(() -> {
            pane().overwriteBox().setSelected(true);
            return null;
        });
        find();
        assertEquals(List.of("Red Lentil Soup"), recipes());
        assertEquals("replaces Berry Overnight Oats (1 serving)", onFx(() -> pane().table().getItems().get(0).replacesText()));
        assertEquals("", onFx(() -> pane().messageLabel().getText()));
    }

    @Test
    void vegetarianProfileNeverShowsMeat() throws Exception {
        ctx.profile().setDiet(DietType.VEGETARIAN);
        stock("Chicken breast", TOMORROW);
        stock("Red lentils", TOMORROW);

        find();

        assertEquals(List.of("Red Lentil Soup"), recipes());
    }

    @Test
    void profileChangeAfterTheProposalRefusesApplyAndKeepsIt() throws Exception {
        stock("Chicken breast", TOMORROW);
        find();
        assertEquals(List.of("Chicken Stir-Fry"), recipes());
        ctx.profile().setDiet(DietType.VEGETARIAN);

        onFx(() -> {
            pane().applyButton().fire();
            return null;
        });

        assertTrue(status().startsWith("Refused: Chicken breast is not allowed for a vegetarian diet"), status());
        assertTrue(onFx(() -> controller.statusBar().getStyleClass().contains("status-error")));
        assertEquals(List.of("Chicken Stir-Fry"), recipes());
        assertTrue(onFx(() -> pane().session().isPending()));
        assertEquals(28, ctx.plan().getEmptySlots().size());
    }

    @Test
    void staleProposalIsRefusedAndCleared() throws Exception {
        stock("Red lentils", TOMORROW);
        find();
        onFx(() -> ctx.newPlan(2));

        onFx(() -> {
            pane().applyButton().fire();
            return null;
        });

        assertEquals("The plan changed since this proposal was made. Run 'plan leftovers' again.", status());
        assertFalse(onFx(() -> pane().session().isPending()));
        assertTrue(recipes().isEmpty());
        assertEquals(8, ctx.plan().getEmptySlots().size());
    }

    @Test
    void aiRerankReordersTheRowsAndShowsActivity() throws Exception {
        ai.reply(RANKING);
        stock("Red lentils", TOMORROW);
        stock("Spinach", TOMORROW);
        onFx(() -> {
            pane().rerankBox().setSelected(true);
            return null;
        });

        find();

        waitFor("the AI order", () -> controller.statusBar().getText().equals("AI re-ranked the proposal; nothing applied yet."));
        assertEquals(List.of("Red Lentil Soup", "Spinach Mushroom Omelette"), recipes());
        assertEquals("Warm soup first", onFx(() -> pane().table().getItems().get(0).reasonText()));
        assertTrue(onFx(() -> pane().notesLabel().getText().contains("Ranked with AI assistance")));
        assertTrue(onFx(() -> pane().activity().isVisible() && !pane().activity().model().lines().isEmpty()));
        assertEquals(1, ai.requests.size());
    }

    @Test
    void failingAiKeepsTheDeterministicRows() throws Exception {
        ai.fail("The AI service had an internal error");
        stock("Red lentils", TOMORROW);
        stock("Spinach", TOMORROW);
        onFx(() -> {
            pane().rerankBox().setSelected(true);
            return null;
        });

        find();

        waitFor("the AI problem", () -> controller.statusBar().getText().equals("AI explanation unavailable: The AI service had an internal error"));
        assertEquals(List.of("Spinach Mushroom Omelette", "Red Lentil Soup"), recipes());
        assertTrue(onFx(() -> controller.statusBar().getStyleClass().contains("status-info")));
        assertTrue(onFx(() -> pane().session().isPending() && !pane().applyButton().isDisabled()));
    }

    @Test
    void sharedBusyFlagDisablesTheOtherAiControlsWhileReRanking() throws Exception {
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
                return new LlmResponse(RANKING, new TokenUsage(100, 20), "blocking-model");
            }
        });
        stock("Red lentils", TOMORROW);
        stock("Spinach", TOMORROW);
        waitFor("AI enabled", () -> !pane().rerankBox().isDisabled());
        onFx(() -> {
            pane().rerankBox().setSelected(true);
            pane().findButton().fire();
            return null;
        });
        assertTrue(entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        assertTrue(onFx(() -> pane().findButton().isDisabled() && pane().applyButton().isDisabled()
                && pane().rerankBox().isDisabled() && controller.planTab().chat().sendButton().isDisabled()
                && controller.nutritionTab().explainButton().isDisabled()));
        assertEquals(List.of("Spinach Mushroom Omelette", "Red Lentil Soup"), recipes());

        release.countDown();
        waitFor("the job to finish", () -> !pane().findButton().isDisabled() && !controller.planTab().chat().sendButton().isDisabled());
        assertEquals(List.of("Red Lentil Soup", "Spinach Mushroom Omelette"), recipes());
    }

    @Test
    void rerankIsDisabledWithAVisibleReasonWhenAiIsOff() throws Exception {
        ctx.llm().disable("Not configured");

        waitFor("the label", () -> pane().unavailableLabel().isVisible());

        assertEquals("AI unavailable: Not configured", onFx(() -> pane().unavailableLabel().getText()));
        assertTrue(onFx(() -> pane().rerankBox().isDisabled() && !pane().rerankBox().isSelected()));
    }
}
