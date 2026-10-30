package com.mealmind.llm;

import java.util.Objects;

/**
 * Unchecked exception for a failed LLM call. The message is a one-line explanation suitable for
 * showing to the user, and it never contains an API key.
 */
public class LlmException extends RuntimeException {

    private final LlmErrorKind kind;

    /**
     * Creates an exception.
     *
     * @param kind    why the call failed
     * @param message a user-readable one-liner, free of secrets
     * @param cause   the underlying exception, or null
     */
    public LlmException(LlmErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    /**
     * Returns why the call failed.
     *
     * @return the error kind
     */
    public LlmErrorKind getKind() {
        return kind;
    }

    /**
     * Returns whether retrying might succeed.
     *
     * @return {@code getKind().isRetryable()}
     */
    public boolean isRetryable() {
        return kind.isRetryable();
    }
}
