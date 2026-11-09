package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import com.mealmind.substitution.SubstituteText;
import com.mealmind.substitution.SubstitutionApplier;
import com.mealmind.substitution.SubstitutionProposal;
import com.mealmind.substitution.SubstitutionReason;
import com.mealmind.substitution.SubstitutionRequest;
import com.mealmind.substitution.SubstitutionResult;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The GUI's substitution texts must equal what {@code recipe substitute} prints for the same scripted session:
 * the CLI and the facade (as the GUI calls it) each get the same scripted answer, and the lines are compared.
 */
class SubstituteCliMatchTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void rowsAndLinesEqualTheCliOutput() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK);
        ScriptedAi ai = new ScriptedAi();
        ctx.llm().useClient(ai);
        ctx.pantry().addItem(new PantryItem("Firm tofu", 400, Unit.GRAM));
        String reply = SubstituteReplies.reply(SubstituteReplies.TOFU, SubstituteReplies.CHICKPEAS);
        ai.reply(reply, reply, reply);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CLIHandler cli = new CLIHandler(ctx, new BufferedReader(new StringReader("")), new PrintStream(buffer, true, StandardCharsets.UTF_8));

        cli.handleLine("recipe substitute --id R101 --ingredient chicken --reason allergy");
        List<String> printed = buffer.toString(StandardCharsets.UTF_8).lines().toList();
        SubstitutionResult result = ctx.agent().suggestSubstitutions(
                new SubstitutionRequest("R101", "chicken", SubstitutionReason.ALLERGY), ctx.pantry(), null);
        SubstitutionProposal proposal = result.proposal().orElseThrow();

        List<String> lines = SubstituteText.proposalLines(proposal);
        int start = printed.indexOf(lines.get(0));
        assertTrue(start >= 0, printed.toString());
        assertEquals(lines, printed.subList(start, start + lines.size()));
        assertEquals(SubstituteText.statsLine(result), printed.get(printed.size() - 1));

        List<SubstituteRow> rows = SubstituteRows.of(proposal);
        for (int i = 0; i < rows.size(); i++) {
            String optionLine = printed.get(start + 1 + 2 * i);
            SubstituteRow row = rows.get(i);
            assertTrue(optionLine.startsWith("  " + row.numberText() + ". " + row.optionText()), optionLine);
            assertTrue(optionLine.contains(row.noteText()) && optionLine.endsWith(row.pantryText().isEmpty() ? row.noteText() : "[" + row.pantryText() + "]"),
                    optionLine);
            assertEquals("     " + row.nutritionText(), printed.get(start + 2 + 2 * i));
        }
        assertEquals("in pantry", rows.get(0).pantryText());

        buffer.reset();
        cli.handleLine("recipe substitute apply 1");
        List<String> applied = buffer.toString(StandardCharsets.UTF_8).lines().filter(l -> !l.startsWith("[update]")).toList();
        ctx.history().undo();
        SubstitutionApplier.ApplyResult gui = new SubstitutionApplier(ctx.recipeBook(), ctx.validator()).apply(
                ctx.agent().suggestSubstitutions(new SubstitutionRequest("R101", "chicken", SubstitutionReason.ALLERGY),
                        ctx.pantry(), null).proposal().orElseThrow(), 1, ctx.history());
        assertEquals(applied, SubstituteText.appliedLines(gui, "R101", ctx.isBundledRecipe("R101")));
    }
}
