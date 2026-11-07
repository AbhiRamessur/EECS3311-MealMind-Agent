package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeScaler;
import com.mealmind.model.Unit;
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

class RecipeDetailsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private ApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.create(dir, CLOCK, warning -> { });
    }

    private Optional<RecipeDetail> detail(String id) {
        return RecipeDetails.of(id, ctx.recipeBook(), ctx.preferences(), ctx.validator());
    }

    /** What the CLI prints for {@code recipe show <id>}. */
    private List<String> cliShow(String id) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new CLIHandler(ctx, new BufferedReader(new StringReader("")), new PrintStream(buffer, true, StandardCharsets.UTF_8))
                .handleLine("recipe show " + id);
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void matchesTheCliRecipeShowText() {
        ctx.preferences().rate("R103", 4);
        ctx.preferences().setFavorite("R103", true);

        RecipeDetail detail = detail("R103").orElseThrow();
        List<String> cli = cliShow("R103");

        assertEquals(cli.get(0), detail.title());
        assertEquals(cli.get(1), detail.summaryText());
        assertEquals(cli.get(2), detail.perServingText());
        assertEquals(cli.subList(4, 4 + detail.ingredientLines().size()),
                detail.ingredientLines().stream().map(line -> "  - " + line).toList());
        assertTrue(cli.containsAll(detail.instructionLines().stream().map(line -> "  " + line).toList()));
        assertEquals("1. ", detail.instructionLines().get(0).substring(0, 3));
        assertEquals("Your rating: 4/5 | Favorite: yes", detail.ratingLine());
        assertTrue(cli.contains(detail.ratingLine()));
        assertEquals("Profile check: OK", detail.safetyLine());
        assertFalse(detail.aiEstimate());
    }

    @Test
    void safetyLineJoinsViolations() {
        ctx.profile().setDiet(DietType.VEGETARIAN);

        String line = detail("R101").orElseThrow().safetyLine();

        assertTrue(line.startsWith("Profile check: Chicken breast is not allowed for a vegetarian diet"), line);
        assertTrue(detail("R101").orElseThrow().ratingLine().endsWith("Favorite: no"));
        assertEquals("Your rating: not rated | Favorite: no", detail("R101").orElseThrow().ratingLine());
    }

    @Test
    void aiEstimateOnlyForAiIdsAndUnknownIdIsEmpty() {
        ctx.recipeBook().put(new Recipe("AI001", "Lime Rice", 2, 15, List.of("Cook", "Serve"),
                List.of(new Ingredient("Rice", 123.456, Unit.GRAM, 450, 8, 99, 1, 1.0))));

        RecipeDetail ai = detail("AI001").orElseThrow();

        assertTrue(ai.aiEstimate());
        assertEquals(List.of("1. Cook", "2. Serve"), ai.instructionLines());
        String rounded = new java.math.BigDecimal(String.valueOf(RecipeScaler.roundForDisplay(123.456, Unit.GRAM)))
                .stripTrailingZeros().toPlainString();
        assertEquals(rounded + " g Rice", ai.ingredientLines().get(0));
        assertEquals(Optional.empty(), detail("R999"));
        assertEquals(Optional.empty(), detail(null));
    }
}
