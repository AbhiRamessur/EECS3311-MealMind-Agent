package com.mealmind.substitution;

import com.mealmind.command.CommandHistory;
import com.mealmind.command.SubstituteIngredientCommand;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Names;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Applies a chosen substitute (the deterministic half of F07). The option is checked for safety again
 * against the current profile, the recipe must still be the one the suggestion was made for, and the
 * change runs as an undoable {@link SubstituteIngredientCommand}.
 *
 * <p><strong>Limits:</strong> recipe steps are not edited (a warning names the steps that still mention the
 * old ingredient); meals already in a plan keep their own recipe objects and are not affected; the new
 * ingredient's nutrition and cost are AI estimates.
 */
public final class SubstitutionApplier {

    private final RecipeBook book;
    private final ConstraintValidator validator;

    /**
     * The outcome of applying an option.
     *
     * @param message  e.g. "Substituted Chicken breast with Tofu in Chicken Stir-Fry"
     * @param updated  the recipe now in the book
     * @param warnings the per-serving calorie change first, then step and safety warnings (unmodifiable)
     */
    public record ApplyResult(String message, Recipe updated, List<String> warnings) {

        /**
         * Copies the list.
         *
         * @throws NullPointerException if a field is null
         */
        public ApplyResult {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(updated, "updated");
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Creates the applier.
     *
     * @param book      the recipe book (changed only through the history)
     * @param validator the safety check
     * @throws NullPointerException if an argument is null
     */
    public SubstitutionApplier(RecipeBook book, ConstraintValidator validator) {
        this.book = Objects.requireNonNull(book, "book");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Applies option {@code optionNumber} of the proposal.
     *
     * @param proposal     the proposal
     * @param optionNumber 1-based option number
     * @param history      where the command is run, so it can be undone
     * @return the message, the updated recipe and the warnings
     * @throws IllegalArgumentException if the option number is out of range
     * @throws IllegalStateException    if the recipe changed since the suggestion or the option is unsafe now
     * @throws NullPointerException     if an argument is null
     */
    public ApplyResult apply(SubstitutionProposal proposal, int optionNumber, CommandHistory history) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(history, "history");
        int count = proposal.options().size();
        if (optionNumber < 1 || optionNumber > count) {
            throw new IllegalArgumentException("Choose an option from 1 to " + count + ", got " + optionNumber);
        }
        Recipe original = proposal.recipe();
        if (book.get(original.getId()).orElse(null) != original) {
            throw new IllegalStateException("The recipe changed since this suggestion was made. Run 'recipe substitute' again.");
        }
        SubstitutionOption option = proposal.options().get(optionNumber - 1);
        Ingredient target = proposal.target();
        Ingredient replacement = new Ingredient(option.name(), target.getQuantity() * option.ratio(), target.getUnit(),
                option.calories(), option.proteinG(), option.carbsG(), option.fatG(), option.cost());
        List<String> unsafe = messages(validator.validateAgainstBlacklists(replacement).getSafetyViolations());
        if (!unsafe.isEmpty()) {
            throw new IllegalStateException("Refused: " + String.join("; ", unsafe));
        }

        history.pushAndExecute(new SubstituteIngredientCommand(book, original.getId(), proposal.ingredientIndex(), replacement));
        Recipe updated = book.get(original.getId()).orElseThrow();
        List<String> warnings = new ArrayList<>();
        warnings.add("Per serving: " + Math.round(original.caloriesPerServing()) + " -> "
                + Math.round(updated.caloriesPerServing()) + " kcal");
        Pattern mention = wholePhrase(Names.normalize(target.getName()));
        List<String> steps = original.getInstructions();
        for (int i = 0; i < steps.size(); i++) {
            if (mention.matcher(Names.normalize(steps.get(i))).find()) {
                warnings.add("Step " + (i + 1) + " may still mention " + target.getName() + "; steps are not rewritten");
            }
        }
        List<String> remaining = messages(validator.validateAgainstBlacklists(updated).getSafetyViolations());
        if (!remaining.isEmpty()) {
            warnings.add("Recipe still has issues: " + String.join("; ", remaining));
        }
        String message = "Substituted " + target.getName() + " with " + replacement.getName() + " in " + original.getName();
        return new ApplyResult(message, updated, warnings);
    }

    /** Matches the phrase when no letter or digit touches it on either side. */
    private static Pattern wholePhrase(String phrase) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])");
    }

    private static List<String> messages(List<Violation> violations) {
        return violations.stream().map(Violation::message).collect(Collectors.toList());
    }
}
