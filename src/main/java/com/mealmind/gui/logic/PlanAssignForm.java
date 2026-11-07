package com.mealmind.gui.logic;

import com.mealmind.model.MealType;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the Plan tab's "Edit selected slot" controls. Every error is collected, per field ("day", "slot",
 * "recipe", "servings"). The day message matches {@code MealPlan}'s.
 */
public final class PlanAssignForm {

    private PlanAssignForm() {
    }

    /**
     * Parses an assignment.
     *
     * @param day      the selected day, or null
     * @param slot     the selected slot, or null
     * @param recipeId the chosen recipe id
     * @param servings blank for the recipe's own servings, otherwise a whole number from 1 to 12
     * @param planDays the current plan's length
     * @return the assignment, or the errors
     */
    public static FormResult<PlanAssignment> parse(Integer day, MealType slot, String recipeId, String servings, int planDays) {
        List<FieldError> errors = new ArrayList<>();
        if (day == null) {
            errors.add(new FieldError("day", "Select a slot in the plan"));
        } else if (day < 1 || day > planDays) {
            errors.add(new FieldError("day", "Day must be between 1 and " + planDays + ", got " + day));
        }
        if (slot == null) {
            errors.add(new FieldError("slot", "Select a slot in the plan"));
        }
        if (recipeId == null || recipeId.isBlank()) {
            errors.add(new FieldError("recipe", "Choose a recipe"));
        }
        Integer count = null;
        if (servings != null && !servings.isBlank()) {
            FormResult<Integer> parsed = ScaleForm.parseServings(servings);
            if (parsed.isValid()) {
                count = parsed.value().orElseThrow();
            } else {
                errors.add(parsed.errors().get(0));
            }
        }
        if (!errors.isEmpty()) {
            return FormResult.failed(errors);
        }
        return FormResult.ok(new PlanAssignment(day, slot, recipeId.trim(), count));
    }
}
