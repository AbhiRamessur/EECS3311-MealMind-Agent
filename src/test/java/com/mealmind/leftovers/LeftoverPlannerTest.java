package com.mealmind.leftovers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.command.CommandHistory;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealPlan.Slot;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.recommend.RecommendationResult;
import com.mealmind.validation.ConstraintValidator;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeftoverPlannerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private RecipeBook book;
    private Pantry pantry;
    private UserProfile profile;
    private RecipePreferences preferences;
    private LeftoverPlanner planner;
    private MealPlan plan;
    private CommandHistory history;

    /** Every ingredient is 100 g, 100 kcal, 10 g protein, 10 g carbs, 5 g fat and $1.00. */
    private static Recipe recipe(String id, String name, String... ingredients) {
        return new Recipe(id, name, 2, 20, List.of("Cook"), Arrays.stream(ingredients)
                .map(ingredient -> new Ingredient(ingredient, 100, Unit.GRAM, 100, 10, 10, 5, 1.00)).toList());
    }

    private void stock(String name, Integer daysUntilExpiry) {
        pantry.addItem(new PantryItem(name, 400, Unit.GRAM, daysUntilExpiry == null ? null : TODAY.plusDays(daysUntilExpiry)));
    }

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        book.put(recipe("L1", "Lentil Soup", "Red lentils", "Onion"));
        book.put(recipe("L2", "Lentil Curry", "Red lentils", "Rice"));
        book.put(recipe("L3", "Lentil Stew", "Red lentils", "Celery"));
        book.put(recipe("M1", "Chicken Rice", "Chicken breast", "Rice"));
        book.put(recipe("S1", "Spinach Omelette", "Eggs", "Spinach"));
        book.put(recipe("N1", "Plain Pasta", "Pasta"));
        pantry = new Pantry();
        profile = new UserProfile();
        preferences = new RecipePreferences(book);
        planner = new LeftoverPlanner(book, pantry, new ConstraintValidator(profile), profile, preferences);
        plan = new MealPlan("Weekly Plan", 2);
        history = new CommandHistory();
    }

    /** Spinach today (weight 4), red lentils tomorrow (3), chicken in 2 days (2), rice without expiry. */
    private void stockDefault() {
        stock("Spinach", 0);
        stock("Red lentils", 1);
        stock("Chicken breast", 2);
        stock("Rice", null);
    }

    private static List<String> ids(LeftoverCandidates candidates) {
        return candidates.candidates().stream().map(c -> c.recipe().getId()).toList();
    }

    private static Meal meal(MealType type) {
        return new Meal(type, "Existing " + type, List.of(recipe("X" + type.ordinal(), "Existing", "Bread")));
    }

    private void fillAll(MealPlan target) {
        for (int day = 1; day <= target.getNumberOfDays(); day++) {
            for (MealType type : MealType.values()) {
                target.setMeal(day, type, meal(type));
            }
        }
    }

    // ---- findCandidates ----

    @Test
    void emptyPantryIsBlocked() {
        LeftoverCandidates result = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        assertEquals("The pantry is empty; nothing to do.", result.blocked().orElseThrow());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void nothingExpiringIsBlocked() {
        stock("Rice", null);
        stock("Red lentils", 5);

        LeftoverCandidates result = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        assertEquals("No pantry items expire within 3 days; nothing to do.", result.blocked().orElseThrow());
        assertEquals(List.of("L2", "L1", "L3"),
                ids(planner.findCandidates(plan, new LeftoverRequest(5, 3, false), TODAY)));
    }

    @Test
    void expiredItemsAreIncludedMostUrgentFirst() {
        stock("Red lentils", 1);
        stock("Spinach", -2);

        LeftoverCandidates result = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        assertEquals(List.of("Spinach", "Red lentils"), result.expiring().stream().map(ExpiringItem::name).toList());
        assertTrue(result.expiring().get(0).expired());
        assertEquals("S1", ids(result).get(0));
    }

    @Test
    void noEmptySlotsIsBlockedUnlessOverwriting() {
        stockDefault();
        fillAll(plan);

        LeftoverCandidates blocked = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);
        assertEquals("The plan has no empty slots. Use --overwrite to let leftover meals replace existing ones "
                + "(undoable with 'undo').", blocked.blocked().orElseThrow());

        LeftoverCandidates overwrite = planner.findCandidates(plan, new LeftoverRequest(3, 3, true), TODAY);
        assertFalse(overwrite.isBlocked());
        assertEquals(8, overwrite.slots().size());
        assertEquals(new Slot(1, MealType.BREAKFAST), overwrite.slots().get(0));
    }

    @Test
    void overwriteAppendsFilledSlotsAfterEmptyOnes() {
        stockDefault();
        plan.setMeal(1, MealType.BREAKFAST, meal(MealType.BREAKFAST));
        plan.setMeal(2, MealType.LUNCH, meal(MealType.LUNCH));

        List<Slot> slots = planner.findCandidates(plan, new LeftoverRequest(3, 3, true), TODAY).slots();

        assertEquals(8, slots.size());
        assertEquals(List.of(new Slot(1, MealType.BREAKFAST), new Slot(2, MealType.LUNCH)), slots.subList(6, 8));
        assertEquals(new Slot(1, MealType.LUNCH), slots.get(0));
        assertEquals(6, planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY).slots().size());
    }

    @Test
    void candidatesAreOrderedByUrgencyThenCoverageThenId() {
        stockDefault();

        LeftoverCandidates result = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        // S1 450 (spinach 4, 50%); L2 400 (lentils 3, 100%); L1 and L3 350 (tie, by id); M1 300 (chicken 2, 100%).
        assertEquals(List.of("S1", "L2", "L1", "L3", "M1"), ids(result));
        assertEquals(100, result.candidates().get(1).coveragePercent());
        assertEquals(List.of("Onion"), result.candidates().get(2).missing());
        assertTrue(result.notes().isEmpty());
    }

    @Test
    void unsafeRecipesAndRecipesWithoutExpiringItemsAreExcluded() {
        stockDefault();
        stock("Pasta", null);
        profile.setDiet(DietType.VEGETARIAN);

        List<String> ids = ids(planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY));

        assertFalse(ids.contains("M1"));
        assertFalse(ids.contains("N1"));
        assertEquals(List.of("S1", "L2", "L1", "L3"), ids);
    }

    @Test
    void lowRatedRecipesAreDroppedWithANote() {
        stockDefault();
        preferences.rate("L2", 1);
        preferences.rate("L1", 2);
        preferences.rate("S1", 3);
        preferences.setFavorite("M1", true);

        LeftoverCandidates result = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        assertEquals(List.of("S1", "L3", "M1"), ids(result));
        assertEquals(List.of("Excluded 2 recipe(s) you rated 1-2 stars."), result.notes());
        assertEquals(3, result.candidates().get(0).rating());
        assertTrue(result.candidates().get(2).favorite());
    }

    @Test
    void lowRatedRecipesAreKeptWhenNothingElseIsLeft() {
        stock("Chicken breast", 1);
        preferences.rate("M1", 2);

        LeftoverCandidates result = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        assertEquals(List.of("M1"), ids(result));
        assertTrue(result.notes().isEmpty());
    }

    @Test
    void noCandidatesSuggestsRecipeGenerate() {
        stock("Tofu", 1);
        stock("Kale", 1);
        stock("Leeks", 1);
        stock("Quinoa", 1);

        assertEquals("No safe recipe in your recipe book uses: Kale, Leeks, Quinoa. "
                        + "Try: recipe generate \"a dish using Kale and Leeks and Quinoa\"",
                planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY).blocked().orElseThrow());
    }

    @Test
    void onlyUnsafeCandidatesAreBlocked() {
        stock("Chicken breast", 1);
        profile.setDiet(DietType.VEGETARIAN);

        assertEquals("No safe recipe in your recipe book uses: Chicken breast. "
                        + "Try: recipe generate \"a dish using Chicken breast\"",
                planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY).blocked().orElseThrow());
    }

    @Test
    void recommendationsMirrorTheCandidates() {
        stockDefault();
        LeftoverCandidates candidates = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        RecommendationResult result = planner.toRecommendations(candidates);

        assertEquals(ids(candidates), result.items().stream().map(r -> r.recipe().getId()).toList());
        assertEquals("Uses Spinach (expires today)", result.items().get(0).reason());
        assertEquals(50, result.items().get(0).coveragePercent());
        assertEquals(candidates.notes(), result.notes());
    }

    // ---- assign ----

    @Test
    void assignFollowsTheOrderIgnoresUnknownIdsAndAppendsTheRest() {
        stockDefault();
        plan.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH));
        LeftoverCandidates candidates = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        LeftoverProposal proposal = planner.assign(candidates, plan, List.of("L1", "R999", "M1", "L1"), Map.of(),
                LeftoverRequest.defaults());

        assertEquals(List.of("L1", "M1", "S1"),
                proposal.assignments().stream().map(a -> a.recipe().getId()).toList());
        assertEquals(List.of(new Slot(1, MealType.BREAKFAST), new Slot(1, MealType.DINNER), new Slot(1, MealType.SNACK)),
                proposal.assignments().stream().map(a -> new Slot(a.day(), a.slot())).toList());
        assertTrue(proposal.assignments().stream().noneMatch(LeftoverAssignment::replacesExisting));
        assertEquals("Uses Red lentils (expires in 1 day)", proposal.assignments().get(0).reason());
        assertEquals(List.of("3 meal(s) proposed for 3 expiring item(s)."), proposal.notes());
        assertSame(plan, proposal.plan());
    }

    @Test
    void assignRespectsTheMealAndSlotLimits() {
        stockDefault();
        LeftoverCandidates candidates = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);
        assertEquals(1, planner.assign(candidates, plan, List.of(), Map.of(), new LeftoverRequest(3, 1, false))
                .assignments().size());
        assertEquals(5, planner.assign(candidates, plan, List.of(), Map.of(), new LeftoverRequest(3, 6, false))
                .assignments().size());

        MealPlan oneSlot = new MealPlan("Small", 1);
        oneSlot.setMeal(1, MealType.BREAKFAST, meal(MealType.BREAKFAST));
        oneSlot.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH));
        oneSlot.setMeal(1, MealType.DINNER, meal(MealType.DINNER));
        LeftoverCandidates few = planner.findCandidates(oneSlot, new LeftoverRequest(3, 6, false), TODAY);
        LeftoverProposal proposal = planner.assign(few, oneSlot, List.of(), Map.of(), new LeftoverRequest(3, 6, false));
        assertEquals(1, proposal.assignments().size());
        assertEquals(MealType.SNACK, proposal.assignments().get(0).slot());
    }

    @Test
    void overwritingMarksReplacedSlotsAndAddsANote() {
        stockDefault();
        MealPlan full = new MealPlan("Full", 1);
        fillAll(full);
        LeftoverRequest request = new LeftoverRequest(3, 2, true);

        LeftoverProposal proposal = planner.assign(planner.findCandidates(full, request, TODAY), full, List.of(), Map.of(),
                request);

        assertTrue(proposal.assignments().stream().allMatch(LeftoverAssignment::replacesExisting));
        assertTrue(proposal.notes().contains("Replaces 2 existing meal(s)."), proposal.notes().toString());
    }

    @Test
    void reasonOverridesAreUsedAndCapped() {
        stockDefault();
        LeftoverCandidates candidates = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        LeftoverProposal proposal = planner.assign(candidates, plan, List.of(),
                Map.of("S1", "x".repeat(200), "L2", "  "), LeftoverRequest.defaults());

        assertEquals(140, proposal.assignments().get(0).reason().length());
        assertEquals("Uses Red lentils (expires in 1 day)", proposal.assignments().get(1).reason());
    }

    @Test
    void uncoveredItemsAreNoted() {
        stockDefault();
        LeftoverRequest one = new LeftoverRequest(3, 1, false);

        LeftoverProposal proposal = planner.propose(plan, one, TODAY);

        assertEquals(List.of("1 meal(s) proposed for 3 expiring item(s).", "Not covered: Red lentils, Chicken breast"),
                proposal.notes());
    }

    @Test
    void assigningABlockedResultThrows() {
        LeftoverCandidates blocked = planner.findCandidates(plan, LeftoverRequest.defaults(), TODAY);

        assertThrows(IllegalStateException.class,
                () -> planner.assign(blocked, plan, List.of(), Map.of(), LeftoverRequest.defaults()));
    }

    // ---- propose ----

    @Test
    void proposeIsDeterministic() {
        stockDefault();

        LeftoverProposal first = planner.propose(plan, LeftoverRequest.defaults(), TODAY);
        LeftoverProposal second = planner.propose(plan, LeftoverRequest.defaults(), TODAY);

        assertEquals(first, second);
        assertEquals(List.of("S1", "L2", "L1"), first.assignments().stream().map(a -> a.recipe().getId()).toList());
        assertTrue(plan.getEmptySlots().size() == 8, "propose must not change the plan");
    }

    @Test
    void proposeThrowsTheBlockedMessage() {
        assertEquals("The pantry is empty; nothing to do.", assertThrows(IllegalStateException.class,
                () -> planner.propose(plan, LeftoverRequest.defaults(), TODAY)).getMessage());
    }

    // ---- apply ----

    @Test
    void applyRunsOneUndoableMacroCommand() {
        stockDefault();
        LeftoverProposal proposal = planner.propose(plan, LeftoverRequest.defaults(), TODAY);

        List<String> lines = planner.apply(proposal, plan, history);

        assertEquals(List.of("Day 1 BREAKFAST: Spinach Omelette (1 serving)", "Day 1 LUNCH: Lentil Curry (1 serving)",
                "Day 1 DINNER: Lentil Soup (1 serving)"), lines);
        assertEquals(1, history.undoSize());
        assertEquals("Add 3 leftover meal(s) to the plan", history.nextUndoDescription().orElseThrow());
        assertEquals("Spinach Omelette", plan.getMeal(1, MealType.BREAKFAST).orElseThrow().getName());
        assertEquals(5, plan.getEmptySlots().size());

        assertTrue(history.undo());
        assertEquals(8, plan.getEmptySlots().size());
        assertTrue(history.redo());
        assertEquals(5, plan.getEmptySlots().size());
    }

    @Test
    void mealsAreScaledToTheHouseholdServings() {
        stockDefault();
        profile.setHouseholdServings(4);
        LeftoverProposal proposal = planner.propose(plan, new LeftoverRequest(3, 1, false), TODAY);

        assertEquals(List.of("Day 1 BREAKFAST: Spinach Omelette (4 servings)"), planner.apply(proposal, plan, history));
        assertEquals(4, plan.getMeal(1, MealType.BREAKFAST).orElseThrow().getRecipes().get(0).getServings());
    }

    @Test
    void applyRefusesAReplacedPlan() {
        stockDefault();
        LeftoverProposal proposal = planner.propose(plan, LeftoverRequest.defaults(), TODAY);
        MealPlan other = new MealPlan("Weekly Plan", 2);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> planner.apply(proposal, other, history));

        assertEquals("The plan changed since this proposal was made. Run 'plan leftovers' again.", e.getMessage());
        assertEquals(0, history.undoSize());
        assertEquals(8, other.getEmptySlots().size());
    }

    @Test
    void applyRefusesARecipeThatBecameUnsafe() {
        stock("Chicken breast", 1);
        stock("Rice", null);
        LeftoverProposal proposal = planner.propose(plan, LeftoverRequest.defaults(), TODAY);
        profile.setDiet(DietType.VEGETARIAN);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> planner.apply(proposal, plan, history));

        assertTrue(e.getMessage().startsWith("Refused: Chicken breast is not allowed for a vegetarian diet"), e.getMessage());
        assertEquals(0, history.undoSize());
        assertEquals(8, plan.getEmptySlots().size());
    }

    @Test
    void applyRefusesASlotThatWasFilledMeanwhile() {
        stockDefault();
        LeftoverProposal proposal = planner.propose(plan, LeftoverRequest.defaults(), TODAY);
        plan.setMeal(1, MealType.LUNCH, meal(MealType.LUNCH));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> planner.apply(proposal, plan, history));

        assertEquals("Day 1 LUNCH is no longer empty; run 'plan leftovers' again.", e.getMessage());
        assertEquals(0, history.undoSize());
        assertTrue(plan.getMeal(1, MealType.BREAKFAST).isEmpty());
    }

    @Test
    void recipeBookAndPantryAreNeverModified() {
        stockDefault();
        List<Recipe> recipesBefore = book.getAll();
        List<PantryItem> pantryBefore = pantry.getItems();

        planner.apply(planner.propose(plan, LeftoverRequest.defaults(), TODAY), plan, history);
        history.undo();

        assertEquals(recipesBefore, book.getAll());
        assertEquals(pantryBefore, pantry.getItems());
    }
}
