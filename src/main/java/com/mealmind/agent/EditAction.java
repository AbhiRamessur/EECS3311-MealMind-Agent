package com.mealmind.agent;

/**
 * What a natural-language plan edit (F06) asks for.
 */
public enum EditAction {
    /** Put a recipe into a slot. */
    REPLACE,
    /** Empty a filled slot. */
    CLEAR,
    /** The request is ambiguous or asks for several things: ask the user a question instead. */
    CLARIFY
}
