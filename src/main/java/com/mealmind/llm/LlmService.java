package com.mealmind.llm;

import com.mealmind.model.ObservableSubject;
import java.util.Objects;
import java.util.Optional;

/**
 * The Context of the Strategy pattern: holds the active {@link LLMClient} and lets it be replaced at
 * runtime ({@link #useClient}), for example to fall back from OpenAI to a local Ollama model. All AI
 * features call {@link #complete} here and never see which provider answers.
 *
 * <p>It starts <em>unavailable</em> ("Not configured"); when no client is set, every call fails with
 * {@link LlmErrorKind#UNAVAILABLE} so callers can fall back to manual input. It is also an
 * {@link ObservableSubject}: observers are notified once whenever the provider changes or AI is
 * disabled. Switching is thread-safe (the client field is volatile).
 */
public final class LlmService extends ObservableSubject {

    private static final String NOT_CONFIGURED = "Not configured";

    private final UsageTracker usage = new UsageTracker();
    private volatile LLMClient client;
    private volatile String unavailableReason = NOT_CONFIGURED;

    /**
     * Makes {@code newClient} the active strategy.
     *
     * @param newClient the client to use from now on
     * @throws NullPointerException if {@code newClient} is null
     */
    public void useClient(LLMClient newClient) {
        Objects.requireNonNull(newClient, "client");
        client = newClient;
        unavailableReason = null;
        notifyObservers("AI provider set to " + newClient.providerName() + " (" + newClient.modelName() + ")");
    }

    /**
     * Turns AI features off.
     *
     * @param reason why, shown to the user
     * @throws IllegalArgumentException if {@code reason} is null or blank
     */
    public void disable(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to disable AI features");
        }
        client = null;
        unavailableReason = reason.trim();
        notifyObservers("AI features disabled: " + unavailableReason);
    }

    /**
     * Returns whether a client is active.
     *
     * @return {@code true} if AI calls can be made
     */
    public boolean isAvailable() {
        return client != null;
    }

    /**
     * Returns why AI features are unavailable.
     *
     * @return the reason, or empty when available
     */
    public Optional<String> unavailableReason() {
        return isAvailable() ? Optional.empty() : Optional.ofNullable(unavailableReason);
    }

    /**
     * Returns the active provider's name.
     *
     * @return e.g. {@code "OpenAI"}, or empty when unavailable
     */
    public Optional<String> providerName() {
        return Optional.ofNullable(client).map(LLMClient::providerName);
    }

    /**
     * Returns the active model's name.
     *
     * @return e.g. {@code "gpt-4o"}, or empty when unavailable
     */
    public Optional<String> modelName() {
        return Optional.ofNullable(client).map(LLMClient::modelName);
    }

    /**
     * Returns the call and token counters.
     *
     * @return the tracker
     */
    public UsageTracker usage() {
        return usage;
    }

    /**
     * Sends a request to the active client and records its usage.
     *
     * @param request the request
     * @return the reply
     * @throws LlmException {@link LlmErrorKind#UNAVAILABLE} when no client is set, otherwise whatever the client throws
     */
    public LlmResponse complete(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        LLMClient active = client;
        if (active == null) {
            throw new LlmException(LlmErrorKind.UNAVAILABLE, "AI features are disabled: " + unavailableReason, null);
        }
        try {
            LlmResponse response = active.complete(request);
            usage.recordSuccess(response.usage());
            return response;
        } catch (LlmException e) {
            usage.recordFailure();
            throw e;
        }
    }
}
