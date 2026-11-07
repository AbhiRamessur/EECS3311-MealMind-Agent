package com.mealmind.cli;

import com.mealmind.agent.AgentEventType;
import com.mealmind.agent.PlanEditResult;
import com.mealmind.app.ApplicationContext;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

/**
 * {@code plan edit <words...>} (F06): asks the AI to interpret one change to the current plan. The
 * change itself is applied by deterministic code through the command history, so {@code undo} reverts
 * it; a refused or failed edit leaves the plan unchanged.
 */
final class PlanEditCommands {

    static final String EDIT_USAGE = "plan edit \"<what to change>\"";

    private final ApplicationContext ctx;
    private final PrintStream out;

    PlanEditCommands(ApplicationContext ctx, PrintStream out) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.out = Objects.requireNonNull(out, "out");
    }

    /**
     * Runs one edit. Activity is printed live as "[agent] ..." lines, except ERROR events: the handler
     * prints the error itself once as "Error: ...".
     */
    void edit(List<String> words) {
        if (words.isEmpty()) {
            throw new UsageException(EDIT_USAGE);
        }
        // The plan and history are read on every call: plan new and plan generate replace them.
        PlanEditResult result = ctx.agent().editPlan(String.join(" ", words), ctx.plan(), ctx.history(),
                event -> {
                    if (event.type() != AgentEventType.ERROR) {
                        out.println("[agent] " + event.message());
                    }
                });
        switch (result.outcome()) {
            case APPLIED -> {
                out.println("Edit applied: " + result.message());
                result.detail().forEach(line -> out.println("Warning: " + line));
                out.println("Use 'undo' to revert.");
            }
            case CLARIFICATION -> {
                out.println("Question: " + result.message());
                out.println("Plan unchanged. Answer with another plan edit command.");
            }
            case REFUSED -> {
                List<String> reasons = result.detail().isEmpty() ? List.of(result.message()) : result.detail();
                reasons.forEach(reason -> out.println("Refused: " + reason));
                out.println("Plan unchanged.");
            }
        }
        out.println("Agent: " + result.llmCalls() + " model calls, " + result.toolSteps() + " tool calls; tokens: "
                + result.usage().promptTokens() + " prompt + " + result.usage().completionTokens() + " completion");
    }
}
