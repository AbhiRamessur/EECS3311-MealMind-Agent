package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.app.ApplicationContext;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiAvailabilityTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void reasonForAFreshContextAndEmptyWithAClient() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warning -> { });

        assertEquals("Not configured", AiAvailability.disabledReason(ctx));
        assertEquals("AI features are disabled: Not configured", AiAvailability.tooltip(AiAvailability.disabledReason(ctx)));

        ctx.llm().useClient(new ScriptedAi());
        assertTrue(AiAvailability.disabledReason(ctx).isEmpty());
    }

    @Test
    void busyFlagNotifiesOnChangeOnly() {
        BusyFlag flag = new BusyFlag();
        int[] calls = {0};
        flag.addListener(() -> calls[0]++);

        flag.set(true);
        flag.set(true);
        flag.set(false);

        assertEquals(2, calls[0]);
        assertEquals(false, flag.busy());
    }
}
