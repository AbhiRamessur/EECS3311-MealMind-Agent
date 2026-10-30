package com.mealmind.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP and JSON plumbing shared by {@link OpenAiClient} and {@link OllamaClient}: building and sending
 * a POST, mapping transport failures and HTTP status codes to {@link LlmException}s, and turning
 * {@link ChatMessage}s into JSON. Every message it produces is free of the API key.
 */
final class LlmHttp {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_DETAIL_LENGTH = 200;

    private final HttpClient client;
    private final String baseUrl;
    private final Duration timeout;
    private final String apiKey;
    private final String unreachableMessage;

    /**
     * @param baseUrl            the provider's base URL, already validated by {@link #normalizeBaseUrl}
     * @param timeout            connect and request timeout
     * @param apiKey             the key to redact from messages, or null
     * @param unreachableMessage the message used when the provider cannot be reached
     */
    LlmHttp(String baseUrl, Duration timeout, String apiKey, String unreachableMessage) {
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.apiKey = apiKey;
        this.unreachableMessage = unreachableMessage;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Checks that {@code baseUrl} is an absolute http(s) URL and removes trailing slashes. */
    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL must not be blank");
        }
        String trimmed = baseUrl.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException("Base URL must be an http(s) URL with a host: " + trimmed);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Base URL is not a valid URL: " + trimmed, e);
        }
        return trimmed;
    }

    /** Checks that a timeout is positive. */
    static Duration requirePositive(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        return timeout;
    }

    /** Converts messages to {@code [{"role": "user", "content": "..."}]}. */
    static ArrayNode messages(List<ChatMessage> messages) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ChatMessage message : messages) {
            array.addObject()
                    .put("role", message.role().name().toLowerCase(Locale.ROOT))
                    .put("content", message.content());
        }
        return array;
    }

    /**
     * POSTs {@code body} to {@code baseUrl + path} and returns the response, whatever its status.
     *
     * @throws LlmException for timeouts, connection failures and interrupts
     */
    HttpResponse<String> post(String path, ObjectNode body, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        headers.forEach(request::header);
        try {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmException(LlmErrorKind.TIMEOUT,
                    "The AI service did not answer within " + describe(timeout), e);
        } catch (IOException e) {
            throw new LlmException(LlmErrorKind.UNREACHABLE, unreachableMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmErrorKind.INTERRUPTED, "The AI request was interrupted", e);
        }
    }

    private static String describe(Duration duration) {
        long millis = duration.toMillis();
        return millis % 1000 == 0 ? millis / 1000 + " seconds" : millis + " ms";
    }

    /** Maps a non-2xx status to an exception; the response body is only used, redacted, for 4xx details. */
    LlmException statusError(int status, String body) {
        if (status == 401 || status == 403) {
            return new LlmException(LlmErrorKind.AUTH, "The AI service rejected the API key (HTTP " + status + ")", null);
        }
        if (status == 429) {
            return new LlmException(LlmErrorKind.RATE_LIMIT,
                    "The AI service is rate limiting requests (HTTP 429); try again shortly", null);
        }
        if (status >= 500) {
            return new LlmException(LlmErrorKind.SERVER_ERROR, "The AI service had an internal error (HTTP " + status + ")", null);
        }
        if (status >= 400) {
            String detail = errorDetail(body);
            return new LlmException(LlmErrorKind.BAD_REQUEST, "The AI service rejected the request (HTTP " + status + ")"
                    + (detail.isEmpty() ? "" : ": " + detail), null);
        }
        // 1xx and 3xx (redirects are deliberately not followed).
        return new LlmException(LlmErrorKind.BAD_RESPONSE, "Unexpected HTTP " + status + " response from the AI service", null);
    }

    /** The provider's error text ({"error":{"message":...}} or {"error":"..."}), redacted and shortened. */
    private String errorDetail(String body) {
        try {
            JsonNode error = MAPPER.readTree(body == null ? "" : body).path("error");
            String text = error.isTextual() ? error.asText() : error.path("message").asText("");
            return Redactor.truncate(Redactor.redact(text, apiKey), MAX_DETAIL_LENGTH);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    /** Parses a 2xx body as JSON, or throws {@link LlmErrorKind#BAD_RESPONSE}. */
    static JsonNode parse(String body) {
        try {
            JsonNode root = MAPPER.readTree(Objects.requireNonNullElse(body, ""));
            if (root == null || !root.isObject()) {
                throw badResponse("The AI service returned a response that is not a JSON object", null);
            }
            return root;
        } catch (JsonProcessingException e) {
            throw badResponse("The AI service returned a response that is not valid JSON", e);
        }
    }

    /** Returns the text at {@code node}, or throws {@link LlmErrorKind#BAD_RESPONSE} if it is missing or blank. */
    static String requireContent(JsonNode node) {
        if (!node.isTextual() || node.asText().isBlank()) {
            throw badResponse("The AI service returned no message content", null);
        }
        return node.asText();
    }

    static LlmException badResponse(String message, Throwable cause) {
        return new LlmException(LlmErrorKind.BAD_RESPONSE, message, cause);
    }

    static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }
}
