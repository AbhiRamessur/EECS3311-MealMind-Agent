package com.mealmind.gui.logic;

import com.mealmind.agent.PlanEditResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The lines the GUI shows for a plan edit, in the CLI's {@code plan edit} wording. The CLI prints them inline
 * (there is no helper to reuse), so tests compare these lines with the CLI's output for the same reply.
 */
public final class PlanEditView {

    private PlanEditView() {
    }

    /**
     * Describes an edit's outcome.
     *
     * @param result the edit result
     * @return "Edit applied: ..." with "Warning: ..." lines and the undo hint; "Question: ..." and the
     *         unchanged note; or "Refused: ..." lines and "Plan unchanged."
     * @throws NullPointerException if {@code result} is null
     */
    public static List<String> describe(PlanEditResult result) {
        Objects.requireNonNull(result, "result");
        List<String> lines = new ArrayList<>();
        switch (result.outcome()) {
            case APPLIED -> {
                lines.add("Edit applied: " + result.message());
                result.detail().forEach(line -> lines.add("Warning: " + line));
                lines.add("Use 'undo' to revert.");
            }
            case CLARIFICATION -> {
                lines.add("Question: " + result.message());
                lines.add("Plan unchanged. Answer with another plan edit command.");
            }
            case REFUSED -> {
                List<String> reasons = result.detail().isEmpty() ? List.of(result.message()) : result.detail();
                reasons.forEach(reason -> lines.add("Refused: " + reason));
                lines.add("Plan unchanged.");
            }
        }
        return List.copyOf(lines);
    }

    /**
     * Returns the CLI's stats line for an edit.
     *
     * @param result the edit result
     * @return e.g. "Agent: 2 model calls, 1 tool calls; tokens: 200 prompt + 40 completion"
     */
    public static String statsLine(PlanEditResult result) {
        return "Agent: " + result.llmCalls() + " model calls, " + result.toolSteps() + " tool calls; tokens: "
                + result.usage().promptTokens() + " prompt + " + result.usage().completionTokens() + " completion";
    }
}
