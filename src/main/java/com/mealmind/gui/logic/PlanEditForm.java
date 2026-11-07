package com.mealmind.gui.logic;

import com.mealmind.agent.AgentFacade;
import java.util.List;

/**
 * Validates the Plan tab's "Ask the planner" instruction with the same rules and messages as
 * {@link AgentFacade#editPlan}.
 */
public final class PlanEditForm {

    private PlanEditForm() {
    }

    /**
     * Validates an instruction.
     *
     * @param text what the user typed
     * @return the trimmed instruction, or one error for the field "instruction"
     */
    public static FormResult<String> parseInstruction(String text) {
        String instruction = text == null ? "" : text.trim();
        if (instruction.isEmpty()) {
            return failed("Describe the change you want");
        }
        if (instruction.length() > AgentFacade.MAX_EDIT_LENGTH) {
            return failed("The request is too long (at most " + AgentFacade.MAX_EDIT_LENGTH + " characters)");
        }
        return FormResult.ok(instruction);
    }

    private static FormResult<String> failed(String message) {
        return FormResult.failed(List.of(new FieldError("instruction", message)));
    }
}
