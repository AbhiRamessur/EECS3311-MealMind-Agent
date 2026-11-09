package com.mealmind.agent;

import static com.mealmind.agent.PlanEditReplies.LENTIL_SOUP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.RecipeBook;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.validation.ConstraintValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanEditCheckerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private UserProfile profile;
    private MealPlan plan;
    private PlanEditChecker checker;

    @BeforeEach
    void setUp() throws Exception {
        profile = new UserProfile();
        plan = new MealPlan("Weekly Plan", 3);
        plan.setMeal(2, MealType.DINNER, PlanMeals.forRecipe(MealType.DINNER, LENTIL_SOUP));
        checker = new PlanEditChecker(PlanEditReplies.book(), plan, new ConstraintValidator(profile));
    }

    private Check<PlanEditProposal> check(String editJson) {
        try {
            JsonNode node = JSON.readTree(editJson);
            return checker.check(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String repairFeedback(Check<PlanEditProposal> check) {
        return assertInstanceOf(Check.Repair.class, check).feedback();
    }

    private static PlanEditProposal accepted(Check<PlanEditProposal> check) {
        return (PlanEditProposal) assertInstanceOf(Check.Accepted.class, check).value();
    }

    @Test
    void validReplaceIsAccepted() {
        PlanEditProposal proposal = accepted(check("{\"action\": \"replace\", \"day\": 2, \"slot\": \"dinner\", "
                + "\"recipeId\": \"E3\", \"explanation\": \"  Quicker – only 5 minutes  \"}"));

        assertEquals(EditAction.REPLACE, proposal.action());
        assertEquals(2, proposal.day());
        assertEquals(MealType.DINNER, proposal.slot());
        assertEquals("E3", proposal.recipe().getId());
        assertNull(proposal.servings());
        assertEquals("Quicker - only 5 minutes", proposal.explanation());
        assertNull(proposal.question());
    }

    @Test
    void explanationIsCutTo140AndServingsAreRead() {
        PlanEditProposal proposal = accepted(check("{\"action\": \"REPLACE\", \"day\": 1, \"slot\": \"LUNCH\", "
                + "\"recipeId\": \"E1\", \"servings\": 3, \"explanation\": \"" + "x".repeat(200) + "\", \"mood\": \"happy\"}"));

        assertEquals(140, proposal.explanation().length());
        assertEquals(3, proposal.servings());
    }

    @Test
    void invalidActionDayAndSlot() {
        assertEquals("\"action\" must be one of replace, clear or clarify.", repairFeedback(check("{\"action\": \"swap\"}")));
        assertEquals("\"day\" must be a whole number from 1 to 3.",
                repairFeedback(check("{\"action\": \"replace\", \"day\": 0, \"slot\": \"DINNER\", \"recipeId\": \"E1\"}")));
        assertEquals("\"day\" must be a whole number from 1 to 3.",
                repairFeedback(check("{\"action\": \"replace\", \"day\": 4, \"slot\": \"DINNER\", \"recipeId\": \"E1\"}")));
        assertEquals("\"slot\" must be one of BREAKFAST, LUNCH, DINNER, SNACK.",
                repairFeedback(check("{\"action\": \"replace\", \"day\": 1, \"slot\": \"brunch\", \"recipeId\": \"E1\"}")));
    }

    @Test
    void unknownRecipeIdAndBadServings() {
        assertEquals("Unknown recipe id 'R999'; choose an id from recipe_search results.",
                repairFeedback(check("{\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"R999\"}")));
        assertTrue(repairFeedback(check("{\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\"}")).startsWith("Unknown recipe id"));
        for (int servings : new int[] {0, 13}) {
            assertEquals("\"servings\" must be a whole number from 1 to 12.", repairFeedback(check(
                    "{\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"E1\", \"servings\": " + servings + "}")));
        }
    }

    @Test
    void unsafeRecipeIsReportedWithTheViolation() {
        profile.setDiet(DietType.VEGETARIAN);

        Check<PlanEditProposal> check = check("{\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"E2\"}");

        Check.Unsafe<PlanEditProposal> unsafe = assertInstanceOf(Check.Unsafe.class, check);
        assertEquals("That recipe violates the user's safety rules: Chicken breast is not allowed for a vegetarian diet (contains chicken). "
                + "Choose a different recipe.", unsafe.feedback());
        assertEquals("E2", unsafe.candidate().recipe().getId());
    }

    @Test
    void clearNeedsAFilledSlot() {
        PlanEditProposal proposal = accepted(check("{\"action\": \"clear\", \"day\": 2, \"slot\": \"DINNER\"}"));
        assertEquals(EditAction.CLEAR, proposal.action());
        assertNull(proposal.recipe());

        assertEquals("Day 1 DINNER is already empty.", repairFeedback(check("{\"action\": \"clear\", \"day\": 1, \"slot\": \"DINNER\"}")));
    }

    @Test
    void clarifyQuestionLengthAndBraces() {
        assertEquals("Which day's dinner?", accepted(check("{\"action\": \"Clarify\", \"question\": \" Which day's dinner? \"}")).question());

        String problem = "\"question\" must be one plain question of 5 to 200 characters without braces or backticks.";
        assertEquals(problem, repairFeedback(check("{\"action\": \"clarify\", \"question\": \"Day?\"}")));
        assertEquals(problem, repairFeedback(check("{\"action\": \"clarify\", \"question\": \"" + "y".repeat(201) + "\"}")));
        assertEquals(problem, repairFeedback(check("{\"action\": \"clarify\", \"question\": \"Which {day}?\"}")));
        assertEquals(problem, repairFeedback(check("{\"action\": \"clarify\"}")));
    }

    @Test
    void nonObjectAnswer() {
        assertEquals("\"edit\" must be a JSON object with an \"action\" field.", repairFeedback(check("\"replace it\"")));
        assertEquals("\"edit\" must be a JSON object with an \"action\" field.", repairFeedback(checker.check(null)));
    }

    // ---- Commit 19: explanations must not contradict the chosen recipe ----

    private String explanationFor(String recipeId, String explanation) {
        return accepted(check("{\"action\": \"replace\", \"day\": 1, \"slot\": \"DINNER\", \"recipeId\": \"" + recipeId
                + "\", \"explanation\": \"" + explanation + "\"}")).explanation();
    }

    @Test
    void explanationNamingAnotherRecipeIsDropped() {
        assertEquals("", explanationFor("E3", "Swapped in Lentil Soup because it is quicker"));
    }

    @Test
    void explanationNamingTheChosenRecipeIsKept() {
        assertEquals("Oat Bowl is ready in 5 minutes", explanationFor("E3", "Oat Bowl is ready in 5 minutes"));
    }

    @Test
    void explanationWithoutRecipeNamesIsUnchanged() {
        assertEquals("Ready in 5 minutes", explanationFor("E3", "Ready in 5 minutes"));
        assertEquals("Lentil Soups are heavier, this is lighter",
                explanationFor("E3", "Lentil Soups are heavier, this is lighter"));
    }

    @Test
    void recipeNameMatchingIgnoresCaseAndWhitespace() {
        assertEquals("", explanationFor("E3", "Better than   LENTIL    soup tonight"));
    }

    @Test
    void namesShorterThanFourCharactersAreIgnored() throws Exception {
        RecipeBook book = PlanEditReplies.book();
        book.put(PlanEditReplies.recipe("E4", "Pie", "Apples", 300, 1.00, 30));
        checker = new PlanEditChecker(book, plan, new ConstraintValidator(profile));

        assertEquals("Quicker than pie", explanationFor("E3", "Quicker than pie"));
    }
}
