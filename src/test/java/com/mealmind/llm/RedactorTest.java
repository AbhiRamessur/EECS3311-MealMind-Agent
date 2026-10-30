package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RedactorTest {

    /** Built at runtime so no key-shaped literal exists in the source (the pre-commit hook checks this). */
    private static final String FAKE_KEY = "sk" + "-" + "x".repeat(30);

    @Test
    void replacesExactKeyEverywhere() {
        String key = "test-key-123-not-real";
        assertEquals("a <redacted> b <redacted>", Redactor.redact("a " + key + " b " + key, key));
    }

    @Test
    void replacesSecretKeyShapedStrings() {
        String text = "Incorrect API key provided: " + FAKE_KEY + ".";
        String redacted = Redactor.redact(text, null);

        assertFalse(redacted.contains(FAKE_KEY));
        assertEquals("Incorrect API key provided: <redacted>.", redacted);
    }

    @Test
    void replacesBearerTokens() {
        assertEquals("Authorization: <redacted>", Redactor.redact("Authorization: Bearer abc.def-123", ""));
    }

    @Test
    void nullTextBecomesEmpty() {
        assertEquals("", Redactor.redact(null, "k"));
        assertEquals("", Redactor.truncate(null, 5));
    }

    @Test
    void truncateBoundaries() {
        assertEquals("abcde", Redactor.truncate("abcde", 5));
        assertEquals("abcd…", Redactor.truncate("abcdef", 5));
        assertEquals("…", Redactor.truncate("abcdef", 1));
        assertThrows(IllegalArgumentException.class, () -> Redactor.truncate("abc", 0));
    }
}
