package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LlmErrorKindTest {

    @Test
    void onlyTransientErrorsAreRetryable() {
        for (LlmErrorKind kind : LlmErrorKind.values()) {
            boolean expected = switch (kind) {
                case UNREACHABLE, TIMEOUT, RATE_LIMIT, SERVER_ERROR -> true;
                default -> false;
            };
            assertEquals(expected, kind.isRetryable(), kind.name());
            assertEquals(expected, new LlmException(kind, "x", null).isRetryable(), kind.name());
            assertEquals(kind, new LlmException(kind, "x", null).getKind());
        }
    }
}
