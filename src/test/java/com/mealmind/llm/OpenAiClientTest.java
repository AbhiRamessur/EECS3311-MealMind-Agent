package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiClientTest {

    /** Built at runtime so no key-shaped literal exists in the source. */
    private static final String FAKE_KEY = "sk" + "-" + "x".repeat(30);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String OK_BODY = """
            {"id":"chatcmpl-1","model":"gpt-4o-2024-08-06",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hello there"}}],
             "usage":{"prompt_tokens":12,"completion_tokens":3,"total_tokens":15}}""";

    private FakeLlmServer server;

    @BeforeEach
    void start() throws IOException {
        server = new FakeLlmServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private OpenAiClient client(Duration timeout) {
        return new OpenAiClient(server.baseUrl(), FAKE_KEY, "gpt-4o", timeout);
    }

    private OpenAiClient client() {
        return client(Duration.ofSeconds(5));
    }

    private static LlmRequest request() {
        return LlmRequest.of(List.of(ChatMessage.system("You are terse."), ChatMessage.user("Hi"),
                ChatMessage.assistant("Hello"), ChatMessage.user("Say JSON")));
    }

    /** Runs a failing call and checks its kind and that the key never leaks. */
    private LlmException failure(OpenAiClient client, LlmErrorKind expected) {
        LlmException e = assertThrows(LlmException.class, () -> client.complete(request()));
        assertEquals(expected, e.getKind(), e.getMessage());
        assertFalse(e.getMessage().contains(FAKE_KEY), e.getMessage());
        return e;
    }

    @Test
    void sendsWellFormedRequest() throws IOException {
        server.enqueueJson(200, OK_BODY);

        client().complete(request());

        FakeLlmServer.Request sent = server.lastRequest();
        assertEquals("POST", sent.method());
        assertEquals("/v1/chat/completions", sent.path());
        assertEquals("Bearer " + FAKE_KEY, sent.header("Authorization"));
        assertTrue(sent.header("Content-Type").startsWith("application/json"));
        JsonNode body = JSON.readTree(sent.body());
        assertEquals("gpt-4o", body.get("model").asText());
        assertEquals(0.2, body.get("temperature").asDouble());
        assertEquals(List.of("system", "user", "assistant", "user"),
                body.get("messages").findValuesAsText("role"));
        assertEquals("Hi", body.get("messages").get(1).get("content").asText());
        assertFalse(body.has("max_tokens"));
        assertFalse(body.has("response_format"));
    }

    @Test
    void includesMaxTokensAndJsonModeOnlyWhenSet() throws IOException {
        server.enqueueJson(200, OK_BODY);

        client().complete(request().withMaxTokens(64).withJsonMode(true));

        JsonNode body = JSON.readTree(server.lastRequest().body());
        assertEquals(64, body.get("max_tokens").asInt());
        assertEquals("json_object", body.get("response_format").get("type").asText());
    }

    @Test
    void parsesNormalResponse() {
        server.enqueueJson(200, OK_BODY);

        LlmResponse response = client().complete(request());

        assertEquals("Hello there", response.content());
        assertEquals(new TokenUsage(12, 3), response.usage());
        assertEquals("gpt-4o-2024-08-06", response.model());
    }

    @Test
    void missingUsageIsZeroAndMissingModelFallsBack() {
        server.enqueueJson(200, "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}]}");

        LlmResponse response = client().complete(request());

        assertEquals(TokenUsage.ZERO, response.usage());
        assertEquals("gpt-4o", response.model());
    }

    @Test
    void authFailuresDoNotIncludeTheBody() {
        server.enqueueJson(401, "{\"error\":{\"message\":\"secret detail " + FAKE_KEY + "\"}}");
        LlmException unauthorized = failure(client(), LlmErrorKind.AUTH);
        assertEquals("The AI service rejected the API key (HTTP 401)", unauthorized.getMessage());

        server.enqueueJson(403, "{\"error\":{\"message\":\"secret detail\"}}");
        assertFalse(failure(client(), LlmErrorKind.AUTH).getMessage().contains("secret detail"));
    }

    @Test
    void rateLimitAndServerErrorsAreRetryable() {
        server.enqueueJson(429, "{}");
        assertTrue(failure(client(), LlmErrorKind.RATE_LIMIT).isRetryable());

        server.enqueueJson(500, "{}");
        assertTrue(failure(client(), LlmErrorKind.SERVER_ERROR).isRetryable());
    }

    @Test
    void badRequestDetailIsRedacted() {
        server.enqueueJson(400, "{\"error\":{\"message\":\"Invalid key " + FAKE_KEY + " for model\"}}");

        LlmException e = failure(client(), LlmErrorKind.BAD_REQUEST);

        assertEquals("The AI service rejected the request (HTTP 400): Invalid key <redacted> for model", e.getMessage());
    }

    @Test
    void unusableSuccessBodiesAreBadResponses() {
        server.enqueue(new FakeLlmServer.Response(200, "<html>not json</html>", 0, Map.of()));
        failure(client(), LlmErrorKind.BAD_RESPONSE);

        server.enqueueJson(200, "{\"id\":\"x\"}");
        failure(client(), LlmErrorKind.BAD_RESPONSE);

        server.enqueueJson(200, "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}");
        failure(client(), LlmErrorKind.BAD_RESPONSE);
    }

    @Test
    void slowServerTimesOut() {
        server.enqueue(new FakeLlmServer.Response(200, OK_BODY, 2000, Map.of()));

        long start = System.nanoTime();
        LlmException e = failure(client(Duration.ofMillis(300)), LlmErrorKind.TIMEOUT);

        assertTrue(e.getMessage().contains("300 ms"), e.getMessage());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1900, "should not wait for the server");
    }

    @Test
    void closedPortIsUnreachable() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        OpenAiClient closed = new OpenAiClient("http://127.0.0.1:" + port, FAKE_KEY, "gpt-4o", Duration.ofSeconds(10));

        failure(closed, LlmErrorKind.UNREACHABLE);
    }

    @Test
    void redirectsAreNotFollowed() {
        // A 3xx is reported as BAD_RESPONSE: the client never follows it (it could leak the key elsewhere).
        server.enqueue(new FakeLlmServer.Response(302, "", 0, Map.of("Location", "http://example.invalid/steal")));

        LlmException e = failure(client(), LlmErrorKind.BAD_RESPONSE);

        assertTrue(e.getMessage().contains("302"), e.getMessage());
        assertEquals(1, server.requests().size());
    }

    @Test
    void constructorValidatesWithoutLeakingTheKey() {
        Duration ok = Duration.ofSeconds(1);
        List<IllegalArgumentException> errors = List.of(
                assertThrows(IllegalArgumentException.class, () -> new OpenAiClient(server.baseUrl(), " ", "gpt-4o", ok)),
                assertThrows(IllegalArgumentException.class, () -> new OpenAiClient(server.baseUrl(), FAKE_KEY, " ", ok)),
                assertThrows(IllegalArgumentException.class,
                        () -> new OpenAiClient(server.baseUrl(), FAKE_KEY, "gpt-4o", Duration.ZERO)),
                assertThrows(IllegalArgumentException.class,
                        () -> new OpenAiClient(server.baseUrl(), FAKE_KEY, "gpt-4o", Duration.ofSeconds(-1))),
                assertThrows(IllegalArgumentException.class, () -> new OpenAiClient("ftp://host", FAKE_KEY, "gpt-4o", ok)),
                assertThrows(IllegalArgumentException.class, () -> new OpenAiClient("not a url", FAKE_KEY, "gpt-4o", ok)));

        errors.forEach(e -> assertFalse(e.getMessage().contains(FAKE_KEY), e.getMessage()));
    }

    @Test
    void toStringAndNamesHaveNoKey() {
        OpenAiClient client = client();

        assertEquals("OpenAiClient{model=gpt-4o, baseUrl=" + server.baseUrl() + "}", client.toString());
        assertEquals("OpenAI", client.providerName());
        assertEquals("gpt-4o", client.modelName());
    }
}
