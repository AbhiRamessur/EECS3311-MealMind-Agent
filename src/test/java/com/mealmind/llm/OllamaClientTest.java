package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class OllamaClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String OK_BODY = """
            {"model":"llama3:latest","message":{"role":"assistant","content":"OK"},
             "done":true,"prompt_eval_count":20,"eval_count":2}""";

    private FakeLlmServer server;

    @BeforeEach
    void start() throws IOException {
        server = new FakeLlmServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private OllamaClient client() {
        return new OllamaClient(server.baseUrl(), "llama3", Duration.ofSeconds(5));
    }

    private static LlmRequest request() {
        return LlmRequest.of(List.of(ChatMessage.system("Be terse."), ChatMessage.user("Hi")));
    }

    @Test
    void sendsWellFormedRequestWithoutAuthorization() throws IOException {
        server.enqueueJson(200, OK_BODY);

        client().complete(request());

        FakeLlmServer.Request sent = server.lastRequest();
        assertEquals("POST", sent.method());
        assertEquals("/api/chat", sent.path());
        assertNull(sent.header("Authorization"));
        JsonNode body = JSON.readTree(sent.body());
        assertEquals("llama3", body.get("model").asText());
        assertFalse(body.get("stream").asBoolean());
        assertEquals(0.2, body.get("options").get("temperature").asDouble());
        assertFalse(body.get("options").has("num_predict"));
        assertFalse(body.has("format"));
        assertEquals(List.of("system", "user"), body.get("messages").findValuesAsText("role"));
    }

    @Test
    void includesNumPredictAndJsonFormatOnlyWhenSet() throws IOException {
        server.enqueueJson(200, OK_BODY);

        client().complete(request().withMaxTokens(16).withJsonMode(true));

        JsonNode body = JSON.readTree(server.lastRequest().body());
        assertEquals(16, body.get("options").get("num_predict").asInt());
        assertEquals("json", body.get("format").asText());
    }

    @Test
    void parsesResponseAndCounts() {
        server.enqueueJson(200, OK_BODY);

        LlmResponse response = client().complete(request());

        assertEquals("OK", response.content());
        assertEquals(new TokenUsage(20, 2), response.usage());
        assertEquals("llama3:latest", response.model());
    }

    @Test
    void missingCountsAreZero() {
        server.enqueueJson(200, "{\"message\":{\"content\":\"OK\"}}");

        LlmResponse response = client().complete(request());

        assertEquals(TokenUsage.ZERO, response.usage());
        assertEquals("llama3", response.model());
    }

    @Test
    void unknownModelExplainsHowToPullIt() {
        server.enqueueJson(404, "{\"error\":\"model 'llama3' not found\"}");

        LlmException e = assertThrows(LlmException.class, () -> client().complete(request()));

        assertEquals(LlmErrorKind.BAD_REQUEST, e.getKind());
        assertTrue(e.getMessage().contains("ollama pull llama3"), e.getMessage());
    }

    @Test
    void closedPortIsUnreachableAndAsksIfRunning() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        OllamaClient closed = new OllamaClient("http://127.0.0.1:" + port, "llama3", Duration.ofSeconds(10));

        LlmException e = assertThrows(LlmException.class, () -> closed.complete(request()));

        assertEquals(LlmErrorKind.UNREACHABLE, e.getKind());
        assertEquals("Could not reach Ollama at http://127.0.0.1:" + port + ". Is it running?", e.getMessage());
    }

    @Test
    void trailingSlashInBaseUrlIsTolerated() {
        server.enqueueJson(200, OK_BODY);
        OllamaClient slashed = new OllamaClient(server.baseUrl() + "//", "llama3", Duration.ofSeconds(5));

        slashed.complete(request());

        assertEquals("/api/chat", server.lastRequest().path());
        assertEquals("OllamaClient{model=llama3, baseUrl=" + server.baseUrl() + "}", slashed.toString());
    }

    @Test
    void slowServerTimesOut() {
        server.enqueue(new FakeLlmServer.Response(200, OK_BODY, 2000, Map.of()));
        OllamaClient impatient = new OllamaClient(server.baseUrl(), "llama3", Duration.ofMillis(300));

        LlmException e = assertThrows(LlmException.class, () -> impatient.complete(request()));

        assertEquals(LlmErrorKind.TIMEOUT, e.getKind());
    }

    @Test
    void serverErrorIsRetryableAndNamesAreReported() {
        server.enqueueJson(500, "{\"error\":\"boom\"}");

        LlmException e = assertThrows(LlmException.class, () -> client().complete(request()));

        assertEquals(LlmErrorKind.SERVER_ERROR, e.getKind());
        assertEquals("Ollama", client().providerName());
        assertEquals("llama3", client().modelName());
    }
}
