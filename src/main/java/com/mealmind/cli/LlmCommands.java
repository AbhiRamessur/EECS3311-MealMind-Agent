package com.mealmind.cli;

import com.mealmind.llm.ChatMessage;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.LlmService;
import com.mealmind.llm.Redactor;
import com.mealmind.llm.UsageTracker;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The {@code llm} commands: show the AI configuration and usage, or send one tiny test request.
 * Nothing printed here ever contains the API key.
 */
final class LlmCommands {

    static final String USAGE = "llm status | llm ping";

    private static final int MAX_REPLY_LENGTH = 80;

    private final LlmService llm;
    private final PrintStream out;

    LlmCommands(LlmService llm, PrintStream out) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** Runs {@code llm <subcommand>}. */
    void handle(List<String> args) {
        if (args.size() != 1) {
            throw new UsageException(USAGE);
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "status" -> status();
            case "ping" -> ping();
            default -> throw new UsageException(USAGE);
        }
    }

    private void status() {
        out.println("Provider: " + llm.providerName().orElse("none"));
        out.println("Model: " + llm.modelName().orElse("none"));
        out.println("Status: " + llm.unavailableReason().map(reason -> "disabled (" + reason + ")").orElse("available"));
        UsageTracker.Snapshot usage = llm.usage().snapshot();
        out.println("AI calls: " + usage.successfulCalls() + " ok, " + usage.failedCalls() + " failed; tokens: "
                + usage.promptTokens() + " prompt + " + usage.completionTokens() + " completion = "
                + usage.totalTokens() + " total");
    }

    /** Sends one very small request; an {@code LlmException} is reported by {@link CLIHandler}. */
    private void ping() {
        LlmRequest request = LlmRequest.of(List.of(ChatMessage.user("Reply with the single word OK.")))
                .withMaxTokens(8)
                .withTemperature(0);
        LlmResponse response = llm.complete(request);
        out.println("AI reply: " + Redactor.truncate(response.content().strip(), MAX_REPLY_LENGTH));
        out.println("Tokens: " + response.usage().promptTokens() + " prompt + "
                + response.usage().completionTokens() + " completion");
    }
}
