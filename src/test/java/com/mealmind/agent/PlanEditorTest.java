package com.mealmind.agent;

import static com.mealmind.agent.PlanEditReplies.CHICKEN_PLATE;
import static com.mealmind.agent.PlanEditReplies.LENTIL_SOUP;
import static com.mealmind.agent.PlanEditReplies.OAT_BOWL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.command.CommandHistory;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanEditorTest {

    private UserProfile profile;
    private RecipeBook book;
    private MealPlan plan;
    private CommandHistory history;
    private PlanEditor editor;

    @BeforeEach
    void setUp() {
        profile = new UserProfile();
        book = PlanEditReplies.book();
        plan = new MealPlan("Weekly Plan", 2);
        plan.setMeal(2, MealType.DINNER, PlanMeals.forRecipe(MealType.DINNER, LENTIL_SOUP));
        history = new CommandHistory();
        editor = new PlanEditor(book, new ConstraintValidator(profile), profile);
    }

    private static PlanEditProposal replace(int day, MealType slot, Recipe recipe, Integer servings, String explanation) {
        return new PlanEditProposal(EditAction.REPLACE, day, slot, recipe, servings, explanation, null);
    }

    private Recipe placed(int day, MealType slot) {
        return plan.getMeal(day, slot).orElseThrow().getRecipes().get(0);
    }

    @Test
    void replaceRunsThroughTheHistoryAndUndoRedoWork() {
        PlanEditResult result = editor.apply(replace(2, MealType.DINNER, OAT_BOWL, null, "Only 5 minutes"), plan, history);

        assertEquals(EditOutcome.APPLIED, result.outcome());
        assertEquals("Replaced Day 2 DINNER with Oat Bowl (Only 5 minutes)", result.message());
        assertEquals(Optional.of("Replace Day 2 DINNER with Oat Bowl"), result.commandDescription());
        assertEquals("E3", placed(2, MealType.DINNER).getId());
        assertEquals(1, history.undoSize());

        assertTrue(history.undo());
        assertEquals("E1", placed(2, MealType.DINNER).getId());
        assertTrue(history.redo());
        assertEquals("E3", placed(2, MealType.DINNER).getId());
    }

    @Test
    void undoEmptiesAPreviouslyEmptySlot() {
        assertEquals("Replaced Day 1 LUNCH with Lentil Soup",
                editor.apply(replace(1, MealType.LUNCH, LENTIL_SOUP, null, ""), plan, history).message());

        history.undo();

        assertTrue(plan.getMeal(1, MealType.LUNCH).isEmpty());
    }

    @Test
    void servingsDefaultToTheHouseholdAndExplicitValuesAreHonored() {
        profile.setHouseholdServings(3);
        editor.apply(replace(1, MealType.LUNCH, OAT_BOWL, null, null), plan, history);
        editor.apply(replace(1, MealType.DINNER, OAT_BOWL, 2, null), plan, history);

        Recipe household = placed(1, MealType.LUNCH);
        Recipe explicit = placed(1, MealType.DINNER);
        assertEquals(3, household.getServings());
        assertEquals(300, household.getIngredients().get(0).getQuantity(), 1e-9);
        assertEquals(2, explicit.getServings());
        assertEquals(200, explicit.getIngredients().get(0).getQuantity(), 1e-9);
        assertEquals(400, explicit.caloriesPerServing(), 1e-9);
        assertEquals(1, book.get("E3").orElseThrow().getServings());
        assertEquals(100, book.get("E3").orElseThrow().getIngredients().get(0).getQuantity(), 1e-9);
    }

    @Test
    void unsafeRecipeIsRefusedAtApplyTimeWithNothingPushed() {
        PlanEditProposal looksFine = replace(2, MealType.DINNER, CHICKEN_PLATE, null, "Protein boost");
        profile.setDiet(DietType.VEGETARIAN);

        PlanEditResult result = editor.apply(looksFine, plan, history);

        assertEquals(EditOutcome.REFUSED, result.outcome());
        assertEquals(List.of("Chicken breast is not allowed for a vegetarian diet (contains chicken)"), result.detail());
        assertEquals(Optional.empty(), result.commandDescription());
        assertEquals("E1", placed(2, MealType.DINNER).getId());
        assertFalse(history.canUndo());
    }

    @Test
    void clearIsUndoableAndAnEmptiedSlotIsRefusedWithoutThrowing() {
        PlanEditProposal clear = new PlanEditProposal(EditAction.CLEAR, 2, MealType.DINNER, null, null, "", null);

        PlanEditResult result = editor.apply(clear, plan, history);
        assertEquals(EditOutcome.APPLIED, result.outcome());
        assertEquals("Cleared Day 2 DINNER", result.message());
        assertTrue(plan.getMeal(2, MealType.DINNER).isEmpty());
        history.undo();
        assertEquals("E1", placed(2, MealType.DINNER).getId());

        plan.clearMeal(2, MealType.DINNER);
        PlanEditResult refused = editor.apply(clear, plan, history);
        assertEquals(EditOutcome.REFUSED, refused.outcome());
        assertFalse(refused.message().isBlank());
        assertEquals(0, history.undoSize());
    }

    @Test
    void clarifyChangesNothing() {
        Meal before = plan.getMeal(2, MealType.DINNER).orElseThrow();

        PlanEditResult result = editor.apply(new PlanEditProposal(EditAction.CLARIFY, null, null, null, null, null,
                "Which day's dinner?"), plan, history);

        assertEquals(EditOutcome.CLARIFICATION, result.outcome());
        assertEquals("Which day's dinner?", result.message());
        assertEquals(before, plan.getMeal(2, MealType.DINNER).orElseThrow());
        assertFalse(history.canUndo());
    }

    @Test
    void advisoriesAreDetailLinesAndNeverBlock() {
        profile.setCalorieTarget(800);
        profile.setHouseholdServings(5);

        PlanEditResult result = editor.apply(replace(1, MealType.DINNER, CHICKEN_PLATE, null, null), plan, history);

        assertEquals(EditOutcome.APPLIED, result.outcome());
        assertEquals(1, result.detail().size());
        assertTrue(result.detail().get(0).startsWith("Day 1: 2500 kcal is above 110% of your 800 kcal target"), result.detail().get(0));
        assertEquals(0, result.llmCalls());
    }
}
