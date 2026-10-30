package com.mealmind.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A fake {@link LLMClient} for tests: each call returns or throws the next scripted outcome and is recorded.
 */
final class ScriptedLlmClient implements LLMClient {

    private final String provider;
    private final String model;
    private final Deque<Object> outcomes = new ArrayDeque<>();
    final List<LlmRequest> calls = new ArrayList<>();

    ScriptedLlmClient(String provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    /** Queues a successful reply. */
    ScriptedLlmClient reply(String content, long promptTokens, long completionTokens) {
        outcomes.add(new LlmResponse(content, new TokenUsage(promptTokens, completionTokens), model));
        return this;
    }

    /** Queues a failure. */
    ScriptedLlmClient fail(LlmErrorKind kind) {
        outcomes.add(new LlmException(kind, "scripted " + kind, null));
        return this;
    }

    @Override
    public String providerName() {
        return provider;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        calls.add(request);
        Object outcome = outcomes.poll();
        if (outcome instanceof LlmException e) {
            throw e;
        }
        if (outcome == null) {
            throw new IllegalStateException("No scripted outcome left");
        }
        return (LlmResponse) outcome;
    }

    static LlmRequest anyRequest() {
        return LlmRequest.of(List.of(ChatMessage.user("Hello")));
    }
}
