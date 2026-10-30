package com.mealmind.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * ConcreteStrategy for the OpenAI Chat Completions API (the primary provider, GPT-4o by default).
 * Sends {@code POST {baseUrl}/v1/chat/completions} with a Bearer key. The key is never included in
 * {@link #toString()} or in any exception message.
 */
public final class OpenAiClient implements LLMClient {

    /** The public OpenAI endpoint. */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com";

    private static final String PATH = "/v1/chat/completions";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final LlmHttp http;

    /**
     * Creates a client. No network call is made here.
     *
     * @param baseUrl the API base URL, usually {@link #DEFAULT_BASE_URL}
     * @param apiKey  the secret key
     * @param model   the model name, e.g. {@code "gpt-4o"}
     * @param timeout connect and request timeout
     * @throws IllegalArgumentException if any argument is blank, malformed or not positive (the
     *                                  message never contains the key)
     */
    public OpenAiClient(String baseUrl, String apiKey, String model, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name must not be blank");
        }
        this.baseUrl = LlmHttp.normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.http = new LlmHttp(this.baseUrl, LlmHttp.requirePositive(timeout), this.apiKey,
                "Could not reach the OpenAI API at " + this.baseUrl);
    }

    @Override
    public String providerName() {
        return "OpenAI";
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
        body.put("temperature", request.temperature());
        if (request.maxTokens() != null) {
            body.put("max_tokens", request.maxTokens());
        }
        if (request.jsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }

        HttpResponse<String> response = http.post(PATH, body, Map.of("Authorization", "Bearer " + apiKey));
        if (!LlmHttp.isSuccess(response.statusCode())) {
            throw http.statusError(response.statusCode(), response.body());
        }
        JsonNode root = LlmHttp.parse(response.body());
        String content = LlmHttp.requireContent(root.path("choices").path(0).path("message").path("content"));
        JsonNode usage = root.path("usage");
        TokenUsage tokens = usage.isObject()
                ? new TokenUsage(usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0))
                : TokenUsage.ZERO;
        String answeredBy = root.path("model").asText("");
        return new LlmResponse(content, tokens, answeredBy.isBlank() ? model : answeredBy);
    }

    @Override
    public String toString() {
        return "OpenAiClient{model=" + model + ", baseUrl=" + baseUrl + "}";
    }
}
