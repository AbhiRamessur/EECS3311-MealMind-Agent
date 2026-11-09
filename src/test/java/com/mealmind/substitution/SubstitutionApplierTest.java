package com.mealmind.substitution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.command.CommandHistory;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubstitutionApplierTest {

    private static final Ingredient NOODLES = new Ingredient("Noodles", 200, Unit.GRAM, 700, 24, 140, 4, 1.00);
    private static final Ingredient BUTTER = new Ingredient("Peanut butter", 100, Unit.GRAM, 600, 24, 20, 50, 2.00);
    private static final Ingredient SOY = new Ingredient("Soy sauce", 20, Unit.MILLILITER, 10, 2, 1, 0, 0.20);

    /** 580 kcal for 150 g: 4*20 + 4*20 + 9*48 = 592. */
    private static final SubstitutionOption SUNFLOWER =
            new SubstitutionOption("Sunflower seed butter", 1.5, "Similar texture", 580, 20, 20, 48, 3.00, true);
    private static final SubstitutionOption CHICKEN =
            new SubstitutionOption("Chicken stock", 1.0, "Savory", 40, 4, 2, 2, 0.50, false);

    private RecipeBook book;
    private Recipe recipe;
    private UserProfile profile;
    private SubstitutionApplier applier;
    private CommandHistory history;

    @BeforeEach
    void setUp() {
        recipe = new Recipe("T1", "Peanut Noodles", 2, 15,
                List.of("Cook the noodles.", "Stir in the Peanut  Butter and soy sauce.", "Serve warm."), List.of(NOODLES, BUTTER, SOY));
        book = new RecipeBook();
        book.put(recipe);
        profile = new UserProfile();
        applier = new SubstitutionApplier(book, new ConstraintValidator(profile));
        history = new CommandHistory();
    }

    private SubstitutionProposal proposal(SubstitutionOption... options) {
        return new SubstitutionProposal(recipe, 1, BUTTER, SubstitutionReason.ALLERGY, List.of(options), List.of());
    }

    @Test
    void applyReplacesTheIngredientWithScaledQuantityAndOptionNutrition() {
        SubstitutionApplier.ApplyResult result = applier.apply(proposal(SUNFLOWER), 1, history);

        Ingredient replacement = book.get("T1").orElseThrow().getIngredients().get(1);
        assertEquals("Sunflower seed butter", replacement.getName());
        assertEquals(150, replacement.getQuantity(), 1e-9);
        assertEquals(Unit.GRAM, replacement.getUnit());
        assertEquals(580, replacement.getCalories(), 1e-9);
        assertEquals(48, replacement.getFatG(), 1e-9);
        assertEquals(3.00, replacement.getCost(), 1e-9);
        assertEquals("Substituted Peanut butter with Sunflower seed butter in Peanut Noodles", result.message());
        assertSame(book.get("T1").orElseThrow(), result.updated());
    }

    @Test
    void theChangeIsOnTheHistoryAndUndoRestoresTheOriginal() {
        applier.apply(proposal(SUNFLOWER), 1, history);

        assertEquals(1, history.undoSize());
        assertEquals("Substitute Peanut butter with Sunflower seed butter in Peanut Noodles",
                history.nextUndoDescription().orElseThrow());
        history.undo();
        assertSame(recipe, book.get("T1").orElseThrow());
    }

    @Test
    void warningsListTheCalorieChangeAndStepsThatMentionTheOldIngredient() {
        SubstitutionApplier.ApplyResult result = applier.apply(proposal(SUNFLOWER), 1, history);

        // Before: (700 + 600 + 10) / 2 = 655; after: (700 + 580 + 10) / 2 = 645.
        assertEquals(List.of("Per serving: 655 -> 645 kcal",
                "Step 2 may still mention Peanut butter; steps are not rewritten"), result.warnings());
    }

    @Test
    void noStepWarningWhenNoStepMentionsTheIngredient() {
        recipe = new Recipe("T1", "Peanut Noodles", 2, 15, List.of("Cook the noodles.", "Mix in the peanuts."),
                List.of(NOODLES, BUTTER, SOY));
        book.put(recipe);

        assertEquals(List.of("Per serving: 655 -> 645 kcal"), applier.apply(proposal(SUNFLOWER), 1, history).warnings());
    }

    @Test
    void remainingSafetyIssuesOfTheWholeRecipeAreReported() {
        profile.addAllergen("soy");

        List<String> warnings = applier.apply(proposal(SUNFLOWER), 1, history).warnings();

        assertTrue(warnings.get(warnings.size() - 1).startsWith("Recipe still has issues: Soy sauce"), warnings.toString());
    }

    @Test
    void anOptionThatBecameUnsafeIsRefusedAndNothingRuns() {
        SubstitutionProposal proposal = proposal(SUNFLOWER, CHICKEN);
        profile.setDiet(DietType.VEGETARIAN);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> applier.apply(proposal, 2, history));

        assertTrue(e.getMessage().startsWith("Refused: Chicken stock is not allowed for a vegetarian diet"), e.getMessage());
        assertEquals(0, history.undoSize());
        assertSame(recipe, book.get("T1").orElseThrow());
    }

    @Test
    void aStaleRecipeIsRefused() {
        SubstitutionProposal proposal = proposal(SUNFLOWER);
        book.put(new Recipe("T1", "Peanut Noodles", 2, 15, recipe.getInstructions(), recipe.getIngredients()));

        assertEquals("The recipe changed since this suggestion was made. Run 'recipe substitute' again.",
                assertThrows(IllegalStateException.class, () -> applier.apply(proposal, 1, history)).getMessage());
        assertEquals(0, history.undoSize());
    }

    @Test
    void optionNumbersOutsideTheListAreRefused() {
        SubstitutionProposal proposal = proposal(SUNFLOWER);

        assertEquals("Choose an option from 1 to 1, got 0",
                assertThrows(IllegalArgumentException.class, () -> applier.apply(proposal, 0, history)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> applier.apply(proposal, 2, history));
        assertFalse(history.canUndo());
        assertSame(recipe, book.get("T1").orElseThrow());
    }
}
