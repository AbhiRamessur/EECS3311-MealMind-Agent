package com.mealmind.llm;

/**
 * Why an LLM call failed. Only transient problems are worth retrying.
 */
public enum LlmErrorKind {
    /** No provider is configured (AI features are disabled). */
    UNAVAILABLE,
    /** The provider could not be reached (connection refused, unknown host, network error). */
    UNREACHABLE,
    /** The provider did not answer in time. */
    TIMEOUT,
    /** The API key was rejected (HTTP 401 or 403). */
    AUTH,
    /** Too many requests (HTTP 429). */
    RATE_LIMIT,
    /** The provider failed internally (HTTP 5xx). */
    SERVER_ERROR,
    /** The provider rejected the request (other HTTP 4xx, e.g. an unknown model). */
    BAD_REQUEST,
    /** The provider answered, but not with a usable completion. */
    BAD_RESPONSE,
    /** The calling thread was interrupted. */
    INTERRUPTED;

    /**
     * Returns whether trying again might succeed.
     *
     * @return {@code true} for {@link #UNREACHABLE}, {@link #TIMEOUT}, {@link #RATE_LIMIT} and {@link #SERVER_ERROR}
     */
    public boolean isRetryable() {
        return this == UNREACHABLE || this == TIMEOUT || this == RATE_LIMIT || this == SERVER_ERROR;
    }
}
