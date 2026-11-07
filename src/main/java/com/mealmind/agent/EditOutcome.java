package com.mealmind.agent;

/**
 * How a natural-language plan edit (F06) ended.
 */
public enum EditOutcome {
    /** The change was executed through the command history and can be undone. */
    APPLIED,
    /** Nothing changed; the assistant asked a clarifying question. */
    CLARIFICATION,
    /** Nothing changed because the edit broke the safety rules or could no longer be applied. */
    REFUSED
}
