package com.mealmind.planning;

import com.mealmind.model.MealPlan;
import com.mealmind.validation.ValidationResult;
import java.util.List;
import java.util.Objects;

/**
 * A generated plan and what the user should know about it.
 *
 * @param plan       the new, detached plan
 * @param validation the macro, budget and allergen check of the plan
 * @param notes      plain-text notes about how the plan was built (unmodifiable)
 */
public record PlanResult(MealPlan plan, ValidationResult validation, List<String> notes) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException if any field or note is null
     */
    public PlanResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(validation, "validation");
        notes = List.copyOf(notes);
    }
}
