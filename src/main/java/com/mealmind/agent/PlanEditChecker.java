package com.mealmind.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Names;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the model's {@code "edit"} answer (F06) deterministically and turns it into a
 * {@link PlanEditProposal}. Structural problems are sent back as one-sentence repair prompts; a recipe
 * that breaks the profile's safety rules is reported as {@link Check.Unsafe}. An explanation that names a
 * different recipe from the book than the one chosen is dropped, because it would contradict the action that
 * actually runs. Nothing is changed here.
 */
public final class PlanEditChecker implements AnswerChecker<PlanEditProposal> {

    /** Largest servings value accepted in an edit. */
    public static final int MAX_SERVINGS = 12;
    /** Recipe names shorter than this (after normalizing) are not matched in explanations. */
    static final int MIN_NAME_LENGTH = 4;

    private final RecipeBook recipes;
    private final MealPlan plan;
    private final ConstraintValidator validator;

    /**
     * Creates a checker for one plan.
     *
     * @param recipes   the recipe book ids must come from
     * @param plan      the plan being edited (for day range and filled slots)
     * @param validator the safety check against the current profile
     * @throws NullPointerException if an argument is null
     */
    public PlanEditChecker(RecipeBook recipes, MealPlan plan, ConstraintValidator validator) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public Check<PlanEditProposal> check(JsonNode answer) {
        if (answer == null || !answer.isObject()) {
            return repair("\"edit\" must be a JSON object with an \"action\" field.");
        }
        String action = answer.path("action").asText("").trim().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "replace" -> checkReplace(answer);
            case "clear" -> checkClear(answer);
            case "clarify" -> checkClarify(answer);
            default -> repair("\"action\" must be one of replace, clear or clarify.");
        };
    }

    private Check<PlanEditProposal> checkReplace(JsonNode answer) {
        String slotProblem = slotProblem(answer);
        if (slotProblem != null) {
            return repair(slotProblem);
        }
        JsonNode idNode = answer.get("recipeId");
        String id = idNode != null && idNode.isTextual() ? idNode.asText().trim() : "";
        Recipe recipe = id.isEmpty() ? null : recipes.get(id).orElse(null);
        if (recipe == null) {
            return repair("Unknown recipe id '" + PreferenceNotes.sanitize(id) + "'; choose an id from recipe_search results.");
        }
        Integer servings = null;
        if (answer.has("servings") && !answer.get("servings").isNull()) {
            JsonNode node = answer.get("servings");
            if (!node.canConvertToExactIntegral() || node.asInt() < 1 || node.asInt() > MAX_SERVINGS) {
                return repair("\"servings\" must be a whole number from 1 to " + MAX_SERVINGS + ".");
            }
            servings = node.asInt();
        }
        PlanEditProposal proposal = new PlanEditProposal(EditAction.REPLACE, answer.get("day").asInt(),
                MealType.parse(answer.get("slot").asText()), recipe, servings, explanation(answer), null);
        List<Violation> violations = validator.validateAgainstBlacklists(recipe).getSafetyViolations();
        if (!violations.isEmpty()) {
            return new Check.Unsafe<>("That recipe violates the user's safety rules: "
                    + violations.stream().map(Violation::message).collect(Collectors.joining("; "))
                    + ". Choose a different recipe.", proposal);
        }
        // The explanation is untrusted text: if it names a different recipe from the book, it contradicts the
        // action that will actually run, so it is dropped (the chosen recipe is the source of truth).
        if (namesOtherRecipe(proposal.explanation(), recipe)) {
            proposal = new PlanEditProposal(EditAction.REPLACE, proposal.day(), proposal.slot(), recipe, servings, "", null);
        }
        return new Check.Accepted<>(proposal);
    }

    /**
     * Returns whether {@code explanation} mentions, as a whole phrase, the name of a recipe in the book other than
     * {@code chosen}. Names are compared after {@link Names#normalize}; names shorter than
     * {@value #MIN_NAME_LENGTH} characters are ignored.
     */
    private boolean namesOtherRecipe(String explanation, Recipe chosen) {
        if (explanation == null || explanation.isBlank()) {
            return false;
        }
        String text = Names.normalize(explanation);
        String chosenName = Names.normalize(chosen.getName());
        for (Recipe other : recipes.getAll()) {
            String name = Names.normalize(other.getName());
            if (other.getId().equals(chosen.getId()) || name.equals(chosenName) || name.length() < MIN_NAME_LENGTH) {
                continue;
            }
            if (Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])").matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private Check<PlanEditProposal> checkClear(JsonNode answer) {
        String slotProblem = slotProblem(answer);
        if (slotProblem != null) {
            return repair(slotProblem);
        }
        int day = answer.get("day").asInt();
        MealType slot = MealType.parse(answer.get("slot").asText());
        if (plan.getMeal(day, slot).isEmpty()) {
            return repair("Day " + day + " " + slot + " is already empty.");
        }
        return new Check.Accepted<>(new PlanEditProposal(EditAction.CLEAR, day, slot, null, null, explanation(answer), null));
    }

    private static Check<PlanEditProposal> checkClarify(JsonNode answer) {
        JsonNode node = answer.get("question");
        String question = node != null && node.isTextual() ? ReplyText.clean(node.asText()) : "";
        if (question.length() < PlanEditProposal.MIN_QUESTION || question.length() > PlanEditProposal.MAX_QUESTION
                || question.contains("{") || question.contains("}") || question.contains("`")) {
            return repair("\"question\" must be one plain question of " + PlanEditProposal.MIN_QUESTION + " to "
                    + PlanEditProposal.MAX_QUESTION + " characters without braces or backticks.");
        }
        return new Check.Accepted<>(new PlanEditProposal(EditAction.CLARIFY, null, null, null, null, null, question));
    }

    /** Returns a repair sentence if "day" or "slot" is invalid, otherwise null. */
    private String slotProblem(JsonNode answer) {
        JsonNode day = answer.get("day");
        int days = plan.getNumberOfDays();
        if (day == null || !day.canConvertToExactIntegral() || day.asInt() < 1 || day.asInt() > days) {
            return "\"day\" must be a whole number from 1 to " + days + ".";
        }
        JsonNode slot = answer.get("slot");
        try {
            MealType.parse(slot != null && slot.isTextual() ? slot.asText() : "");
            return null;
        } catch (IllegalArgumentException e) {
            return "\"slot\" must be one of " + Arrays.stream(MealType.values()).map(Enum::name)
                    .collect(Collectors.joining(", ")) + ".";
        }
    }

    /** The optional explanation: cleaned to ASCII and cut to 140 characters. */
    private static String explanation(JsonNode answer) {
        JsonNode node = answer.get("explanation");
        String text = node != null && node.isTextual() ? ReplyText.clean(node.asText()) : "";
        return text.length() <= PlanEditProposal.MAX_EXPLANATION ? text : text.substring(0, PlanEditProposal.MAX_EXPLANATION).trim();
    }

    private static Check<PlanEditProposal> repair(String feedback) {
        return new Check.Repair<>(feedback);
    }
}
