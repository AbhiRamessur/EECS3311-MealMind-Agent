package com.mealmind.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * ConcreteStrategy for a local Ollama server (the offline fallback, e.g. Llama 3 or DeepSeek-R1).
 * Sends {@code POST {baseUrl}/api/chat} with streaming off. No API key is needed.
 */
public final class OllamaClient implements LLMClient {

    private static final String PATH = "/api/chat";

    private final String baseUrl;
    private final String model;
    private final LlmHttp http;

    /**
     * Creates a client. No network call is made here.
     *
     * @param baseUrl the Ollama server, e.g. {@code http://localhost:11434} (trailing slashes are ignored)
     * @param model   the model name, e.g. {@code "llama3"}
     * @param timeout connect and request timeout
     * @throws IllegalArgumentException if any argument is blank, malformed or not positive
     */
    public OllamaClient(String baseUrl, String model, Duration timeout) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must not be blank");
        }
        this.baseUrl = LlmHttp.normalizeBaseUrl(baseUrl);
        this.model = model.trim();
        this.http = new LlmHttp(this.baseUrl, LlmHttp.requirePositive(timeout), null,
                "Could not reach Ollama at " + this.baseUrl + ". Is it running?");
    }

    @Override
    public String providerName() {
        return "Ollama";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        ObjectNode body = LlmHttp.MAPPER.createObjectNode();
        body.put("model", model);
        body.set("messages", LlmHttp.messages(request.messages()));
        body.put("stream", false);
        ObjectNode options = body.putObject("options");
        options.put("temperature", request.temperature());
        if (request.maxTokens() != null) {
            options.put("num_predict", request.maxTokens());
        }
        if (request.jsonMode()) {
            body.put("format", "json");
        }

        HttpResponse<String> response = http.post(PATH, body, Map.of());
        if (response.statusCode() == 404) {
            throw new LlmException(LlmErrorKind.BAD_REQUEST,
                    "Ollama does not have the model '" + model + "'. Run: ollama pull " + model, null);
        }
        if (!LlmHttp.isSuccess(response.statusCode())) {
            throw http.statusError(response.statusCode(), response.body());
        }
        JsonNode root = LlmHttp.parse(response.body());
        String content = LlmHttp.requireContent(root.path("message").path("content"));
        TokenUsage tokens = new TokenUsage(root.path("prompt_eval_count").asLong(0), root.path("eval_count").asLong(0));
        String answeredBy = root.path("model").asText("");
        return new LlmResponse(content, tokens, answeredBy.isBlank() ? model : answeredBy);
    }

    @Override
    public String toString() {
        return "OllamaClient{model=" + model + ", baseUrl=" + baseUrl + "}";
    }
}
