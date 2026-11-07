package com.mealmind.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.app.ApplicationContext;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the GUI against real controls when the JavaFX toolkit can start; on a machine without a display
 * every test here is <em>skipped</em>, never failed. The window is never shown.
 */
class GuiSmokeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
    private static boolean toolkitAvailable;

    @TempDir
    Path dir;

    @BeforeAll
    static void startToolkit() {
        toolkitAvailable = GuiSelfTest.startToolkit().isEmpty();
        if (toolkitAvailable) {
            Platform.setImplicitExit(false);
        }
    }

    /** Runs {@code action} on the FX thread and waits for it. */
    private static <T> T onFx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Exception | AssertionError e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    @Test
    void selfTestPasses() {
        Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit not available");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int status = GuiSelfTest.run(new PrintStream(buffer, true, StandardCharsets.UTF_8), false);

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertEquals(GuiSelfTest.OK, status, output);
        assertTrue(output.endsWith("GUI self-test: ok" + System.lineSeparator()), output);
    }

    @Test
    void profileErrorsShowNextToTheFieldAndNothingIsApplied() throws Exception {
        Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit not available");
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warning -> { });

        String error = onFx(() -> {
            try (MainController controller = new MainController(ctx, null)) {
                controller.profileTab().caloriesField().setText("100");
                controller.profileTab().applyButton().fire();
                return controller.profileTab().errorLabel("calories").getText() + "|" + controller.statusBar().getText();
            }
        });

        assertTrue(error.startsWith("Calorie target must be between 800 and 6000 kcal, got 100|Profile not saved"), error);
        assertTrue(ctx.profile().getCalorieTarget().isEmpty());
    }

    @Test
    void pantryTableFollowsChangesMadeOutsideTheGui() throws Exception {
        Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit not available");
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warning -> { });

        String counts = onFx(() -> {
            try (MainController controller = new MainController(ctx, "AI features are disabled: test.")) {
                ctx.pantry().addItem(new PantryItem("Milk", 1, Unit.LITER,
                        LocalDate.of(2026, 10, 27)));
                String first = controller.pantryTab().table().getItems().get(0).statusText();
                return first + "|" + controller.pantryTab().countsLabel().getText() + "|" + controller.statusBar().getText();
            }
        });

        assertEquals("expires in 1 day|1 item, 1 expired or expiring within 3 days|AI features are disabled: test.", counts);
    }
}
