package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void factoriesSetRoles() {
        assertEquals(Role.SYSTEM, ChatMessage.system("Be brief").role());
        assertEquals(Role.USER, ChatMessage.user("Hi").role());
        assertEquals(Role.ASSISTANT, ChatMessage.assistant("Hello").role());
        assertEquals("Hi", ChatMessage.user("Hi").content());
    }

    @Test
    void rejectsBlankContentAndNullRole() {
        assertThrows(IllegalArgumentException.class, () -> ChatMessage.user("  "));
        assertThrows(IllegalArgumentException.class, () -> ChatMessage.user(null));
        assertThrows(IllegalArgumentException.class, () -> new ChatMessage(null, "x"));
    }
}
