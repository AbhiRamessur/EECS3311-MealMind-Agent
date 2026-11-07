package com.mealmind.gui.logic;

import com.mealmind.agent.GeneratedRecipe;
import com.mealmind.agent.RecipeGenerator;
import com.mealmind.cli.CliFormat;
import com.mealmind.model.Recipe;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The form and preview model behind the Generate section. Prompt messages match {@link RecipeGenerator}'s,
 * and the preview uses the CLI's {@code recipe generate} layout and number formatting, so a pending recipe
 * reads the same in both front ends. Only fields the backend already validated are shown.
 */
public final class GenerationModel {

    /** Shown when there is no pending recipe. */
    public static final String NO_PENDING = "No pending recipe.";

    private GenerationModel() {
    }

    /**
     * Validates the prompt.
     *
     * @param text what the user typed
     * @return the trimmed prompt, or one error for the field "prompt"
     */
    public static FormResult<String> parsePrompt(String text) {
        String prompt = text == null ? "" : text.trim();
        if (prompt.isEmpty()) {
            return FormResult.failed(List.of(new FieldError("prompt", "Describe the recipe you want")));
        }
        if (prompt.length() > RecipeGenerator.MAX_PROMPT_LENGTH) {
            return FormResult.failed(List.of(new FieldError("prompt",
                    "The request is too long (at most " + RecipeGenerator.MAX_PROMPT_LENGTH + " characters)")));
        }
        return FormResult.ok(prompt);
    }

    /**
     * Describes the pending recipe.
     *
     * @param pending the facade's pending recipe
     * @return the preview
     */
    public static PendingView describePending(Optional<GeneratedRecipe> pending) {
        if (pending == null || pending.isEmpty()) {
            return new PendingView(false, NO_PENDING, List.of(), false, "");
        }
        GeneratedRecipe generated = pending.get();
        Recipe recipe = generated.recipe();
        List<String> lines = new ArrayList<>();
        lines.add("Servings: " + recipe.getServings() + " | Prep: " + recipe.getPrepMinutes() + " min");
        lines.add("Per serving: " + CliFormat.nutrition(recipe, recipe.getServings()));
        lines.add("Ingredients:");
        recipe.getIngredients().forEach(ingredient -> lines.add("  - " + RecipeDetails.ingredientLine(ingredient)));
        lines.add("Steps:");
        for (int i = 0; i < recipe.getInstructions().size(); i++) {
            lines.add("  " + (i + 1) + ". " + recipe.getInstructions().get(i));
        }
        lines.add(RecipeDetail.AI_ESTIMATE_LINE);
        return new PendingView(true, "Generated recipe " + recipe.getId() + ": " + recipe.getName(), lines,
                generated.safe(), banner(generated));
    }

    /** "Safety check: passed", or the CLI's warning block (one line per violation). */
    private static String banner(GeneratedRecipe generated) {
        if (generated.safe()) {
            return "Safety check: passed";
        }
        List<String> lines = new ArrayList<>();
        lines.add("SAFETY WARNING: this recipe could not be made safe for your profile after "
                + generated.safetyRetries() + " attempts. It must not be used:");
        for (Violation violation : generated.validation().getSafetyViolations()) {
            lines.add("    " + violation.message());
        }
        lines.add("Pending recipe " + generated.recipe().getId() + " is flagged unsafe.");
        return String.join("\n", lines);
    }
}
