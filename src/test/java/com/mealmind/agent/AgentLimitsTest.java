package com.mealmind.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AgentLimitsTest {

    @Test
    void defaults() {
        assertEquals(new AgentLimits(4, 3, 2, 12, 1500, 0.3), AgentLimits.defaults());
    }

    @Test
    void boundsAcceptedAtBothEnds() {
        assertDoesNotThrow(() -> new AgentLimits(0, 0, 0, 1, 200, 0));
        assertDoesNotThrow(() -> new AgentLimits(10, 5, 5, 30, 8000, 1));
    }

    @Test
    void boundsRejectedJustOutside() {
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(-1, 3, 2, 12, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(11, 3, 2, 12, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, -1, 2, 12, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 6, 2, 12, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, -1, 12, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 6, 12, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 2, 0, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 2, 31, 1500, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 2, 12, 199, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 2, 12, 8001, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 2, 12, 1500, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 2, 12, 1500, 1.01));
        assertThrows(IllegalArgumentException.class, () -> new AgentLimits(4, 3, 2, 12, 1500, Double.NaN));
    }
}
