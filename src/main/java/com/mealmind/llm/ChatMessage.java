package com.mealmind.llm;

/**
 * One message in a conversation with a language model.
 *
 * @param role    who wrote it
 * @param content the text, not blank
 */
public record ChatMessage(Role role, String content) {

    /**
     * Validates the message.
     *
     * @throws IllegalArgumentException if {@code role} is null or {@code content} is null or blank
     */
    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("Message role must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }
    }

    /**
     * Creates a system message.
     *
     * @param content the instructions
     * @return the message
     */
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    /**
     * Creates a user message.
     *
     * @param content the user's text
     * @return the message
     */
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    /**
     * Creates an assistant message (an earlier model reply).
     *
     * @param content the reply text
     * @return the message
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
