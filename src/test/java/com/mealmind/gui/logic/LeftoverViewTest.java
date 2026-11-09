package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.leftovers.LeftoverRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LeftoverView: the header, apply and hint texts compared with what the CLI prints for the same session. */
class LeftoverViewTest {

    @TempDir
    Path dir;

    private LeftoverCliSession session;

    @BeforeEach
    void setUp() {
        session = new LeftoverCliSession(dir);
    }

    @Test
    void headerLinesEqualTheCliOutput() {
        session.cli("pantry add \"Red lentils\" 400 g 2026-11-10", "pantry add Spinach 200 g 2026-11-10", "plan generate --days 3");
        List<String> printed = session.cli("plan leftovers");

        LeftoverRequest request = LeftoverRequest.defaults();
        List<String> header = LeftoverView.headerLines(session.propose(request), request, List.of());

        assertEquals(printed.subList(0, header.size()), header);
        assertEquals(LeftoverView.proposalTitle(), printed.get(header.size()));
        assertEquals("Expiring within 3 days: Red lentils (expires in 1 day); Spinach (expires in 1 day)", header.get(0));
    }

    @Test
    void applyTextsEqualTheCliOutput() {
        session.cli("pantry add \"Red lentils\" 400 g 2026-11-10", "plan leftovers");
        List<String> printed = session.cli("plan leftovers apply");

        String description = session.ctx.history().nextUndoDescription().orElseThrow();
        assertTrue(printed.contains(LeftoverView.applyMessage(description)), printed.toString());
        assertEquals("Applied: Add 1 leftover meal(s) to the plan.", LeftoverView.applyMessage(description));
        assertEquals(LeftoverView.applyHint(), printed.get(printed.size() - 1));
        assertEquals("AI explanation unavailable: down", LeftoverView.aiProblem("down"));
    }

    @Test
    void expiringHintWording() {
        assertEquals("1 item expires within 1 day", LeftoverView.expiringHint(1, 1));
        assertEquals("0 items expire within 3 days", LeftoverView.expiringHint(0, 3));
    }
}
