package com.mealmind.agent;

import com.mealmind.command.ClearMealCommand;
import com.mealmind.command.Command;
import com.mealmind.command.CommandHistory;
import com.mealmind.command.ReplaceMealCommand;
import com.mealmind.llm.TokenUsage;
import com.mealmind.model.Meal;
import com.mealmind.model.MealComponent;
import com.mealmind.model.MealPlan;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.RecipeScaler;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies a {@link PlanEditProposal} deterministically (F06). The AI never changes the plan itself:
 * every change is a {@link ReplaceMealCommand} or {@link ClearMealCommand} run through the
 * {@link CommandHistory}, so it can be undone, and a replacement recipe is checked for safety
 * <strong>again</strong> here against the current profile, which may have changed since the model chose it.
 * The returned counters are zero; the caller fills them in.
 */
public final class PlanEditor {

    private final RecipeBook recipes;
    private final ConstraintValidator validator;
    private final UserProfile profile;

    /**
     * Creates an editor.
     *
     * @param recipes   the recipe book the recipe is looked up in again
     * @param validator the safety check
     * @param profile   the profile whose household servings are the default
     * @throws NullPointerException if an argument is null
     */
    public PlanEditor(RecipeBook recipes, ConstraintValidator validator, UserProfile profile) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * Applies a proposal.
     *
     * @param proposal the checked proposal
     * @param plan     the plan to change
     * @param history  where the command is run, so it can be undone
     * @return APPLIED, CLARIFICATION or REFUSED; the plan and history are unchanged unless APPLIED
     * @throws NullPointerException if an argument is null
     */
    public PlanEditResult apply(PlanEditProposal proposal, MealPlan plan, CommandHistory history) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(history, "history");
        return switch (proposal.action()) {
            case REPLACE -> replace(proposal, plan, history);
            case CLEAR -> clear(proposal, plan, history);
            case CLARIFY -> result(EditOutcome.CLARIFICATION, proposal.question(), List.of(), Optional.empty());
        };
    }

    private PlanEditResult replace(PlanEditProposal proposal, MealPlan plan, CommandHistory history) {
        Optional<Recipe> found = recipes.get(proposal.recipe().getId());
        if (found.isEmpty()) {
            return result(EditOutcome.REFUSED, "Recipe " + proposal.recipe().getId() + " is no longer in the recipe book",
                    List.of(), Optional.empty());
        }
        Recipe recipe = found.get();
        List<String> unsafe = safetyMessages(recipe);
        if (unsafe.isEmpty()) {
            int servings = proposal.servings() != null ? proposal.servings() : profile.getHouseholdServings();
            Meal meal = PlanMeals.forRecipe(proposal.slot(), RecipeScaler.scale(recipe, servings));
            unsafe = safetyMessages(meal);
            if (unsafe.isEmpty()) {
                Command command = new ReplaceMealCommand(plan, proposal.day(), proposal.slot(), meal);
                history.pushAndExecute(command);
                List<String> advisories = validator.validateMacrosAndAllergens(plan).getAdvisories().stream()
                        .map(Violation::message).toList();
                return result(EditOutcome.APPLIED, withExplanation("Replaced Day " + proposal.day() + " "
                        + proposal.slot() + " with " + recipe.getName(), proposal), advisories, Optional.of(command.description()));
            }
        }
        return result(EditOutcome.REFUSED, recipe.getName() + " is not safe for your profile", unsafe, Optional.empty());
    }

    private PlanEditResult clear(PlanEditProposal proposal, MealPlan plan, CommandHistory history) {
        Command command = new ClearMealCommand(plan, proposal.day(), proposal.slot());
        try {
            history.pushAndExecute(command);
        } catch (IllegalStateException | NoSuchElementException e) {
            return result(EditOutcome.REFUSED, e.getMessage(), List.of(), Optional.empty());
        }
        return result(EditOutcome.APPLIED, withExplanation("Cleared Day " + proposal.day() + " " + proposal.slot(), proposal),
                List.of(), Optional.of(command.description()));
    }

    private List<String> safetyMessages(MealComponent component) {
        return validator.validateAgainstBlacklists(component).getSafetyViolations().stream().map(Violation::message).toList();
    }

    private static String withExplanation(String message, PlanEditProposal proposal) {
        String explanation = proposal.explanation();
        return explanation == null || explanation.isBlank() ? message : message + " (" + explanation + ")";
    }

    private static PlanEditResult result(EditOutcome outcome, String message, List<String> detail,
                                         Optional<String> description) {
        return new PlanEditResult(outcome, message, detail, description, 0, 0, TokenUsage.ZERO);
    }
}
