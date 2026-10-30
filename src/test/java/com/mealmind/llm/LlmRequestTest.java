package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmRequestTest {

    private static final List<ChatMessage> HELLO = List.of(ChatMessage.user("Hello"));

    @Test
    void defaults() {
        LlmRequest request = LlmRequest.of(HELLO);

        assertEquals(0.2, request.temperature());
        assertNull(request.maxTokens());
        assertFalse(request.jsonMode());
    }

    @Test
    void rejectsEmptyMessagesAndBadSettings() {
        assertThrows(IllegalArgumentException.class, () -> LlmRequest.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> LlmRequest.of(null));
        assertThrows(IllegalArgumentException.class, () -> LlmRequest.of(HELLO).withTemperature(-0.1));
        assertThrows(IllegalArgumentException.class, () -> LlmRequest.of(HELLO).withTemperature(2.1));
        assertThrows(IllegalArgumentException.class, () -> LlmRequest.of(HELLO).withTemperature(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> LlmRequest.of(HELLO).withMaxTokens(0));
    }

    @Test
    void temperatureBoundsAreInclusive() {
        assertEquals(0, LlmRequest.of(HELLO).withTemperature(0).temperature());
        assertEquals(2, LlmRequest.of(HELLO).withTemperature(2).temperature());
    }

    @Test
    void messagesAreCopiedAndWithMethodsDoNotMutate() {
        List<ChatMessage> source = new ArrayList<>(HELLO);
        LlmRequest original = LlmRequest.of(source);
        source.add(ChatMessage.user("later"));

        LlmRequest changed = original.withJsonMode(true).withMaxTokens(50).withTemperature(1.0);

        assertEquals(1, original.messages().size());
        assertThrows(UnsupportedOperationException.class, () -> original.messages().add(ChatMessage.user("x")));
        assertFalse(original.jsonMode());
        assertNull(original.maxTokens());
        assertTrue(changed.jsonMode());
        assertEquals(50, changed.maxTokens());
        assertEquals(1.0, changed.temperature());
        assertNull(changed.withMaxTokens(null).maxTokens());
    }
}
