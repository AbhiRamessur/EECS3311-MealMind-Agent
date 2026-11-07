package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.agent.GeneratedRecipe;
import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private ApplicationContext ctx;
    private ScriptedAi ai;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.create(dir, CLOCK, warning -> { });
        ai = new ScriptedAi();
        ctx.llm().useClient(ai);
    }

    private List<String> cli(String line) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new CLIHandler(ctx, new BufferedReader(new StringReader("")), new PrintStream(buffer, true, StandardCharsets.UTF_8))
                .handleLine(line);
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void promptMessagesEqualTheGenerator() {
        assertEquals("pasta with beans", GenerationModel.parsePrompt("  pasta with beans ").value().orElseThrow());
        assertEquals(List.of(new FieldError("prompt", "Describe the recipe you want")), GenerationModel.parsePrompt("   ").errors());
        assertEquals(List.of(new FieldError("prompt", "The request is too long (at most 500 characters)")),
                GenerationModel.parsePrompt("x".repeat(501)).errors());
        assertTrue(GenerationModel.parsePrompt("  " + "x".repeat(500) + "  ").isValid());

        // The same messages the generator itself throws.
        assertEquals("Describe the recipe you want",
                assertThrows(IllegalArgumentException.class, () -> ctx.agent().generateRecipe(" ", null)).getMessage());
        assertEquals("The request is too long (at most 500 characters)",
                assertThrows(IllegalArgumentException.class, () -> ctx.agent().generateRecipe("x".repeat(501), null)).getMessage());
    }

    @Test
    void noPendingRecipe() {
        PendingView view = GenerationModel.describePending(Optional.empty());

        assertFalse(view.present());
        assertEquals("No pending recipe.", view.title());
        assertEquals(List.of(), view.lines());
        assertFalse(view.safe());
    }

    @Test
    void safePendingMatchesTheCliPreview() {
        ai.reply(ScriptedAi.recipe("Chickpea Pasta", "Chickpea pasta", "Spinach"));
        GeneratedRecipe generated = ctx.agent().generateRecipe("pasta", null);

        PendingView view = GenerationModel.describePending(Optional.of(generated));
        List<String> cli = cli("recipe pending");

        assertTrue(view.present());
        assertTrue(view.safe());
        assertEquals("Generated recipe AI001: Chickpea Pasta", view.title());
        assertEquals(cli.get(0), view.title());
        assertEquals(cli.subList(1, 1 + view.lines().size()), view.lines());
        assertEquals("Safety check: passed", view.safetyBanner());
    }

    @Test
    void unsafePendingShowsTheCliWarningBlock() {
        ctx.profile().setDiet(DietType.VEGETARIAN);
        for (int i = 0; i < 4; i++) {
            ai.reply(ScriptedAi.recipe("Chicken Pasta", "Chicken breast", "Pasta"));
        }
        GeneratedRecipe generated = ctx.agent().generateRecipe("chicken pasta", null);

        PendingView view = GenerationModel.describePending(Optional.of(generated));
        List<String> cli = cli("recipe pending");

        assertFalse(view.safe());
        List<String> banner = view.safetyBanner().lines().toList();
        assertTrue(banner.get(0).startsWith("SAFETY WARNING: this recipe could not be made safe for your profile after 4 attempts."));
        assertTrue(cli.containsAll(banner), String.join("\n", cli));
        assertTrue(banner.contains("Pending recipe AI001 is flagged unsafe."));
    }
}
