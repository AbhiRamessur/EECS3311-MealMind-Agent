package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.leftovers.LeftoverRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LeftoverRows: weekday slot labels, order, and the "replaces" text compared with the CLI marker. */
class LeftoverRowsTest {

    @TempDir
    Path dir;

    private LeftoverCliSession session;

    @BeforeEach
    void setUp() {
        session = new LeftoverCliSession(dir);
    }

    @Test
    void rowsUseWeekdayLabelsInSlotOrder() {
        session.cli("pantry add \"Red lentils\" 400 g 2026-11-10", "pantry add Spinach 200 g 2026-11-10", "plan generate --days 3");

        List<LeftoverRow> rows = LeftoverRows.of(session.propose(LeftoverRequest.defaults()));

        assertEquals(List.of(new LeftoverRow("Day 1 (Monday) BREAKFAST", "Spinach Mushroom Omelette",
                        "Uses Spinach (expires in 1 day)", ""),
                new LeftoverRow("Day 1 (Monday) SNACK", "Red Lentil Soup", "Uses Red lentils (expires in 1 day)", "")), rows);
    }

    @Test
    void replacesTextMatchesTheCliMarker() {
        session.cli("pantry add \"Red lentils\" 400 g 2026-11-10", "plan new --days 1", "plan set 1 breakfast R102",
                "plan set 1 lunch R105", "plan set 1 dinner R108", "plan set 1 snack R110");
        List<String> printed = session.cli("plan leftovers --overwrite");

        LeftoverRow row = LeftoverRows.of(session.propose(new LeftoverRequest(3, 3, true))).get(0);

        assertEquals("replaces Berry Overnight Oats (1 serving)", row.replacesText());
        assertTrue(printed.contains("  Day 1 BREAKFAST: Red Lentil Soup - Uses Red lentils (expires in 1 day) ["
                + row.replacesText() + "]"), printed.toString());
    }
}
