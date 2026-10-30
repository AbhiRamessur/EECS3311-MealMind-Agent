package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** TokenUsage and the LlmResponse it is reported in. */
class TokenUsageTest {

    @Test
    void totalsAndRejectsNegatives() {
        assertEquals(15, new TokenUsage(10, 5).totalTokens());
        assertEquals(0, TokenUsage.ZERO.totalTokens());
        assertThrows(IllegalArgumentException.class, () -> new TokenUsage(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenUsage(0, -1));
    }

    @Test
    void responseRejectsBlankContentOrModelAndNullUsage() {
        assertThrows(IllegalArgumentException.class, () -> new LlmResponse(" ", TokenUsage.ZERO, "m"));
        assertThrows(IllegalArgumentException.class, () -> new LlmResponse("ok", TokenUsage.ZERO, " "));
        assertThrows(IllegalArgumentException.class, () -> new LlmResponse("ok", null, "m"));
    }
}
