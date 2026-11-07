package com.mealmind.gui.logic;

import com.mealmind.llm.LLMClient;
import com.mealmind.llm.LlmErrorKind;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.TokenUsage;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A scripted fake model for the GUI tests (thread-safe, no network): answers with queued replies or
 * failures, each reply reporting 100 prompt + 20 completion tokens. The agent tests' own helpers are
 * package-private, so the GUI tests use this one.
 */
public final class ScriptedAi implements LLMClient {

    /** Every request received, in order. */
    public final List<LlmRequest> requests = new CopyOnWriteArrayList<>();
    private final Deque<Object> outcomes = new ConcurrentLinkedDeque<>();

    /**
     * Queues replies.
     *
     * @param contents the reply texts, used in order
     * @return this client
     */
    public ScriptedAi reply(String... contents) {
        for (String content : contents) {
            outcomes.add(new LlmResponse(content, new TokenUsage(100, 20), "scripted"));
        }
        return this;
    }

    /**
     * Queues a failure.
     *
     * @param message the error message
     * @return this client
     */
    public ScriptedAi fail(String message) {
        outcomes.add(new LlmException(LlmErrorKind.SERVER_ERROR, message, null));
        return this;
    }

    /**
     * A recipe reply whose ingredients are 100 g each with 20 g protein, 50 g carbs, 10 g fat, 370 kcal and $1.50.
     *
     * @param name        the recipe name
     * @param ingredients the ingredient names
     * @return the reply text
     */
    public static String recipe(String name, String... ingredients) {
        String list = Stream.of(ingredients).map(i -> "{\"name\": \"" + i + "\", \"quantity\": 100, \"unit\": \"g\", "
                + "\"calories\": 370, \"proteinG\": 20, \"carbsG\": 50, \"fatG\": 10, \"cost\": 1.5}").collect(Collectors.joining(", "));
        return "{\"recipe\": {\"name\": \"" + name + "\", \"servings\": 2, \"prepMinutes\": 20, \"ingredients\": [" + list
                + "], \"instructions\": [\"Cook everything.\", \"Serve warm.\"]}}";
    }

    @Override
    public String providerName() {
        return "Scripted";
    }

    @Override
    public String modelName() {
        return "scripted-model";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        Object outcome = outcomes.poll();
        if (outcome instanceof LlmException e) {
            throw e;
        }
        if (outcome == null) {
            throw new LlmException(LlmErrorKind.SERVER_ERROR, "No scripted reply left", null);
        }
        return (LlmResponse) outcome;
    }
}
