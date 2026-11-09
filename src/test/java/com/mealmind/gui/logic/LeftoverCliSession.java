package com.mealmind.gui.logic;

import com.mealmind.app.ApplicationContext;
import com.mealmind.cli.CLIHandler;
import com.mealmind.leftovers.LeftoverFlow;
import com.mealmind.leftovers.LeftoverPlanner;
import com.mealmind.leftovers.LeftoverProposal;
import com.mealmind.leftovers.LeftoverRequest;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A CLI session on a temporary data directory with a fixed clock (2026-11-09), used by the leftover row and view
 * tests to compare the GUI texts with what {@code plan leftovers} prints for the same session.
 */
final class LeftoverCliSession {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    final ApplicationContext ctx;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final CLIHandler cli;

    LeftoverCliSession(Path dir) {
        ctx = ApplicationContext.create(dir, CLOCK);
        cli = new CLIHandler(ctx, new BufferedReader(new StringReader("")), new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    /** Runs the lines and returns only the output they produced. */
    List<String> cli(String... lines) {
        buffer.reset();
        for (String line : lines) {
            cli.handleLine(line);
        }
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    /** The deterministic proposal for the session's current plan. */
    LeftoverProposal propose(LeftoverRequest request) {
        LeftoverPlanner planner = new LeftoverPlanner(ctx.recipeBook(), ctx.pantry(), ctx.validator(), ctx.profile(), ctx.preferences());
        return LeftoverFlow.propose(planner, ctx.plan(), request, LocalDate.now(CLOCK)).proposal().orElseThrow();
    }
}
