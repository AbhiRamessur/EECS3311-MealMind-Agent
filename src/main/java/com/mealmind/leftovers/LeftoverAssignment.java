package com.mealmind.leftovers;

import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import java.util.List;
import java.util.Objects;

/**
 * One proposed meal: a recipe for one plan slot.
 *
 * @param day              the day number
 * @param slot             the meal slot
 * @param recipe           the recipe (unscaled; it is scaled to the household servings when applied)
 * @param uses             the expiring items it uses (unmodifiable)
 * @param reason           why it was chosen, at most 140 characters
 * @param replacesExisting whether the slot was filled when the proposal was made
 */
public record LeftoverAssignment(int day, MealType slot, Recipe recipe, List<ExpiringItem> uses, String reason,
                                 boolean replacesExisting) {

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public LeftoverAssignment {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(recipe, "recipe");
        uses = List.copyOf(uses);
        Objects.requireNonNull(reason, "reason");
    }
}
