package com.mealmind.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentEventTest {

    @Test
    void whitespaceIsCollapsedToSingleSpaces() {
        AgentEvent event = new AgentEvent(AgentEventType.TOOL_RESULT, "  line one\nline\ttwo   three \r\n");

        assertEquals("line one line two three", event.message());
    }

    @Test
    void longMessagesAreCutAt200WithEllipsis() {
        AgentEvent event = new AgentEvent(AgentEventType.ERROR, "x".repeat(500));

        assertEquals(200, event.message().length());
        assertTrue(event.message().endsWith("…"));
        assertEquals("y".repeat(200), new AgentEvent(AgentEventType.DONE, "y".repeat(200)).message());
    }

    @Test
    void blankMessageAndNullTypeRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AgentEvent(AgentEventType.DONE, " \n\t "));
        assertThrows(IllegalArgumentException.class, () -> new AgentEvent(AgentEventType.DONE, null));
        assertThrows(NullPointerException.class, () -> new AgentEvent(null, "x"));
    }
}
