package com.mealmind.llm;

/**
 * Who wrote a {@link ChatMessage}.
 */
public enum Role {
    /** Instructions that set up the model's behaviour. */
    SYSTEM,
    /** Input from the user (or from MealMind on the user's behalf). */
    USER,
    /** An earlier reply from the model. */
    ASSISTANT
}
