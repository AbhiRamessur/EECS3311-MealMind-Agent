package com.mealmind.llm;

import java.util.regex.Pattern;

/**
 * Removes secrets from text before it goes into an error message, and shortens long text.
 */
public final class Redactor {

    private static final String REDACTED = "<redacted>";
    /** OpenAI-style secret keys. */
    private static final Pattern SECRET_KEY = Pattern.compile("sk-[A-Za-z0-9_-]{8,}");
    /** "Bearer <token>" as found in echoed Authorization headers. */
    private static final Pattern BEARER_TOKEN = Pattern.compile("Bearer\\s+[A-Za-z0-9._~+/=-]+");

    private Redactor() {
    }

    /**
     * Replaces the exact {@code apiKey}, anything that looks like a secret key, and any Bearer token
     * with {@code "<redacted>"}.
     *
     * @param text   the text to clean, may be null
     * @param apiKey the key to remove, may be null or blank
     * @return the cleaned text ({@code ""} for null)
     */
    public static String redact(String text, String apiKey) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (apiKey != null && !apiKey.isBlank()) {
            result = result.replace(apiKey, REDACTED);
        }
        result = BEARER_TOKEN.matcher(result).replaceAll(REDACTED);
        return SECRET_KEY.matcher(result).replaceAll(REDACTED);
    }

    /**
     * Shortens text to at most {@code maxLen} characters, ending with "…" when it was cut.
     *
     * @param text   the text, may be null
     * @param maxLen the maximum length, at least 1
     * @return the possibly shortened text ({@code ""} for null)
     * @throws IllegalArgumentException if {@code maxLen < 1}
     */
    public static String truncate(String text, int maxLen) {
        if (maxLen < 1) {
            throw new IllegalArgumentException("maxLen must be at least 1, got " + maxLen);
        }
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
    }
}
