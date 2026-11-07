package com.mealmind.agent;

import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import java.util.Objects;

/**
 * A checked interpretation of a plan edit request, produced by {@link PlanEditChecker}. It is only a
 * proposal: nothing changes until {@link PlanEditor} applies it. Fields that do not apply to the action
 * are null.
 *
 * @param action      what to do
 * @param day         the day number (REPLACE and CLEAR)
 * @param slot        the slot (REPLACE and CLEAR)
 * @param recipe      the recipe from the recipe book (REPLACE)
 * @param servings    the requested servings, or null for the profile's default (REPLACE)
 * @param explanation a short reason, 0 to 140 characters (REPLACE and CLEAR; may be empty)
 * @param question    the clarifying question, 5 to 200 characters (CLARIFY)
 */
public record PlanEditProposal(EditAction action, Integer day, MealType slot, Recipe recipe, Integer servings,
                               String explanation, String question) {

    /** Longest explanation kept, in characters. */
    public static final int MAX_EXPLANATION = 140;
    /** Shortest clarifying question, in characters. */
    public static final int MIN_QUESTION = 5;
    /** Longest clarifying question, in characters. */
    public static final int MAX_QUESTION = 200;

    /**
     * Validates the fields that every action needs.
     *
     * @throws NullPointerException     if {@code action} is null, or a field its action needs is null
     * @throws IllegalArgumentException if the explanation or question length is out of range
     */
    public PlanEditProposal {
        Objects.requireNonNull(action, "action");
        if (action == EditAction.CLARIFY) {
            Objects.requireNonNull(question, "question");
            if (question.length() < MIN_QUESTION || question.length() > MAX_QUESTION) {
                throw new IllegalArgumentException("Question must be " + MIN_QUESTION + " to " + MAX_QUESTION + " characters");
            }
        } else {
            Objects.requireNonNull(day, "day");
            Objects.requireNonNull(slot, "slot");
            if (action == EditAction.REPLACE) {
                Objects.requireNonNull(recipe, "recipe");
            }
        }
        if (explanation != null && explanation.length() > MAX_EXPLANATION) {
            throw new IllegalArgumentException("Explanation must be at most " + MAX_EXPLANATION + " characters");
        }
    }
}
