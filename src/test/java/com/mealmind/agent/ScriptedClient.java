package com.mealmind.agent;

import com.mealmind.llm.LLMClient;
import com.mealmind.llm.LlmErrorKind;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.LlmService;
import com.mealmind.llm.TokenUsage;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A fake model for agent tests: answers with scripted replies (each reporting 100 prompt + 20
 * completion tokens) or scripted failures, records every request, and can block on a latch.
 */
final class ScriptedClient implements LLMClient {

    static final TokenUsage USAGE_PER_CALL = new TokenUsage(100, 20);

    final List<LlmRequest> requests = new CopyOnWriteArrayList<>();
    final CountDownLatch entered = new CountDownLatch(1);
    private final Deque<Object> outcomes = new ConcurrentLinkedDeque<>();
    private volatile CountDownLatch gate;

    /** Queues replies, used in order. */
    ScriptedClient reply(String... contents) {
        for (String content : contents) {
            outcomes.add(new LlmResponse(content, USAGE_PER_CALL, "fake-model"));
        }
        return this;
    }

    /** Queues a failure. */
    ScriptedClient fail(LlmErrorKind kind, String message) {
        outcomes.add(new LlmException(kind, message, null));
        return this;
    }

    /** Makes every call wait for {@code latch} before answering. */
    ScriptedClient blockUntil(CountDownLatch latch) {
        gate = latch;
        return this;
    }

    /** An LlmService using this client. */
    LlmService service() {
        LlmService service = new LlmService();
        service.useClient(this);
        return service;
    }

    @Override
    public String providerName() {
        return "Scripted";
    }

    @Override
    public String modelName() {
        return "fake-model";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        entered.countDown();
        CountDownLatch waitFor = gate;
        if (waitFor != null) {
            try {
                if (!waitFor.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test latch was never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException(LlmErrorKind.INTERRUPTED, "interrupted", e);
            }
        }
        Object outcome = outcomes.poll();
        if (outcome instanceof LlmException e) {
            throw e;
        }
        if (outcome == null) {
            throw new IllegalStateException("No scripted reply left (call " + requests.size() + ")");
        }
        return (LlmResponse) outcome;
    }
}
