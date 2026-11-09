package com.mealmind.substitution;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import java.util.List;
import java.util.Objects;

/**
 * Safe substitutes for one ingredient of one recipe. Nothing changes until it is passed to
 * {@link SubstitutionApplier#apply}.
 *
 * @param recipe          the recipe the suggestion was made for (compared by identity when applying)
 * @param ingredientIndex the position of the ingredient in the recipe (0-based)
 * @param target          the ingredient to replace
 * @param reason          why it is replaced
 * @param options         1 to 3 safe options, best first (unmodifiable)
 * @param notes           plain-text notes (unmodifiable)
 */
public record SubstitutionProposal(Recipe recipe, int ingredientIndex, Ingredient target, SubstitutionReason reason,
                                   List<SubstitutionOption> options, List<String> notes) {

    /** The most options a proposal holds. */
    public static final int MAX_OPTIONS = 3;

    /**
     * Validates and copies the fields.
     *
     * @throws NullPointerException     if a field or element is null
     * @throws IllegalArgumentException if the index is not in the recipe or there are not 1 to 3 options
     */
    public SubstitutionProposal {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        options = List.copyOf(options);
        notes = List.copyOf(notes);
        if (ingredientIndex < 0 || ingredientIndex >= recipe.getIngredients().size()) {
            throw new IllegalArgumentException("Ingredient index " + ingredientIndex + " is not in recipe " + recipe.getId());
        }
        if (options.isEmpty() || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("A proposal needs 1 to " + MAX_OPTIONS + " options, got " + options.size());
        }
    }
}
