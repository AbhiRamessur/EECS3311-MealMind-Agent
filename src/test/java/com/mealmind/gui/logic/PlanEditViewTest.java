package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.agent.EditOutcome;
import com.mealmind.agent.PlanEditResult;
import com.mealmind.agent.PlanMeals;
import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
import com.mealmind.llm.TokenUsage;
import com.mealmind.model.MealType;
import com.mealmind.profile.DietType;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The GUI's edit lines must equal what {@code plan edit} prints. Each case runs the same scripted reply once
 * through the facade (for the result) and once through the CLI, on two identical sessions.
 */
class PlanEditViewTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private int sessions;

    private ApplicationContext session(Consumer<ApplicationContext> setup, String... replies) {
        ApplicationContext ctx = ApplicationContext.create(dir.resolve("s" + sessions++), CLOCK, warning -> { });
        ctx.llm().useClient(new ScriptedAi().reply(replies));
        ctx.plan().setMeal(1, MealType.DINNER, PlanMeals.forRecipe(MealType.DINNER, ctx.recipeBook().get("R103").orElseThrow()));
        setup.accept(ctx);
        return ctx;
    }

    private static List<String> cliLines(ApplicationContext ctx, String instruction) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new CLIHandler(ctx, new BufferedReader(new StringReader("")), new PrintStream(buffer, true, StandardCharsets.UTF_8))
                .handleLine("plan edit " + instruction);
        return buffer.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> !line.startsWith("[agent]") && !line.startsWith("[update]")).toList();
    }

    private void assertSameAsCli(Consumer<ApplicationContext> setup, String... replies) {
        ApplicationContext viaFacade = session(setup, replies);
        PlanEditResult result = viaFacade.agent().editPlan("change monday dinner", viaFacade.plan(), viaFacade.history(), null);
        List<String> expected = cliLines(session(setup, replies), "change monday dinner");

        List<String> gui = new java.util.ArrayList<>(PlanEditView.describe(result));
        gui.add(PlanEditView.statsLine(result));
        assertEquals(expected, gui);
    }

    @Test
    void appliedWithWarningsAndTheUndoHint() {
        assertSameAsCli(ctx -> ctx.profile().setCalorieTarget(800),
                "{\"edit\": {\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"R104\", \"servings\": 6}}");
    }

    @Test
    void clarification() {
        assertSameAsCli(ctx -> { }, "{\"edit\": {\"action\": \"clarify\", \"question\": \"Which day do you mean?\"}}");
    }

    @Test
    void refused() {
        String unsafe = "{\"edit\": {\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"R101\"}}";
        assertSameAsCli(ctx -> ctx.profile().setDiet(DietType.VEGETARIAN), unsafe, unsafe, unsafe, unsafe);
    }

    @Test
    void refusedWithoutDetailUsesTheMessageAndStatsLine() {
        PlanEditResult result = new PlanEditResult(EditOutcome.REFUSED, "Day 1 DINNER is already empty", List.of(), Optional.empty(),
                2, 1, new TokenUsage(200, 40));

        assertEquals(List.of("Refused: Day 1 DINNER is already empty", "Plan unchanged."), PlanEditView.describe(result));
        assertEquals("Agent: 2 model calls, 1 tool calls; tokens: 200 prompt + 40 completion", PlanEditView.statsLine(result));
        assertTrue(PlanEditView.describe(new PlanEditResult(EditOutcome.APPLIED, "Cleared Day 1 DINNER", List.of(), Optional.of("x"),
                1, 0, TokenUsage.ZERO)).contains("Use 'undo' to revert."));
    }
}
