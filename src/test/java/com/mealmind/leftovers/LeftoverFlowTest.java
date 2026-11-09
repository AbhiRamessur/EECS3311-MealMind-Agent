package com.mealmind.leftovers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.agent.AgentEvent;
import com.mealmind.agent.AgentFacade;
import com.mealmind.agent.AgentLimits;
import com.mealmind.agent.AgentMemory;
import com.mealmind.gui.logic.ScriptedAi;
import com.mealmind.llm.LlmService;
import com.mealmind.model.Ingredient;
import com.mealmind.model.MealPlan;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.UserProfile;
import com.mealmind.tools.ToolManager;
import com.mealmind.validation.ConstraintValidator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeftoverFlowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final LeftoverRequest REQUEST = LeftoverRequest.defaults();

    private RecipeBook book;
    private Pantry pantry;
    private LeftoverPlanner planner;
    private MealPlan plan;
    private ScriptedAi ai;
    private LlmService llm;
    private AgentFacade agent;
    private final List<AgentEvent> events = new ArrayList<>();

    private static Recipe recipe(String id, String name, String... ingredients) {
        return new Recipe(id, name, 2, 20, List.of("Cook"), Arrays.stream(ingredients)
                .map(i -> new Ingredient(i, 100, Unit.GRAM, 100, 10, 10, 5, 1.00)).toList());
    }

    @BeforeEach
    void setUp() {
        book = new RecipeBook();
        book.put(recipe("L1", "Lentil Soup", "Red lentils", "Onion"));
        book.put(recipe("S1", "Spinach Omelette", "Eggs", "Spinach"));
        pantry = new Pantry();
        UserProfile profile = new UserProfile();
        ConstraintValidator validator = new ConstraintValidator(profile);
        planner = new LeftoverPlanner(book, pantry, validator, profile, new RecipePreferences(book));
        plan = new MealPlan("Weekly Plan", 2);
        ai = new ScriptedAi();
        llm = new LlmService();
        llm.useClient(ai);
        agent = new AgentFacade(llm, new ToolManager(), new AgentMemory(), validator, profile, book, AgentLimits.defaults());
    }

    private void stockBoth() {
        pantry.addItem(new PantryItem("Spinach", 200, Unit.GRAM, TODAY));
        pantry.addItem(new PantryItem("Red lentils", 400, Unit.GRAM, TODAY.plusDays(1)));
    }

    private static List<String> ids(LeftoverFlow.Outcome outcome) {
        return outcome.proposal().orElseThrow().assignments().stream().map(a -> a.recipe().getId()).toList();
    }

    private LeftoverFlow.Outcome rerank(LeftoverFlow.Outcome deterministic) {
        return LeftoverFlow.rerank(planner, deterministic.candidates().orElseThrow(), plan, REQUEST, agent, events::add);
    }

    @Test
    void blockedOutcomesCarryTheMessageAndNoProposal() {
        LeftoverFlow.Outcome empty = LeftoverFlow.propose(planner, plan, REQUEST, TODAY);
        assertEquals("The pantry is empty; nothing to do.", empty.blocked().orElseThrow());
        assertTrue(empty.proposal().isEmpty());

        pantry.addItem(new PantryItem("Rice", 500, Unit.GRAM));
        assertEquals("No pantry items expire within 3 days; nothing to do.",
                LeftoverFlow.propose(planner, plan, REQUEST, TODAY).blocked().orElseThrow());
    }

    @Test
    void deterministicProposalHasNoExtraNotes() {
        stockBoth();

        LeftoverFlow.Outcome outcome = LeftoverFlow.propose(planner, plan, REQUEST, TODAY);

        assertEquals(List.of("S1", "L1"), ids(outcome));
        assertTrue(outcome.blocked().isEmpty() && outcome.aiProblem().isEmpty() && outcome.notes().isEmpty());
        assertEquals(2, outcome.candidates().orElseThrow().candidates().size());
        assertTrue(LeftoverFlow.canRerank(outcome.candidates().orElseThrow()));
    }

    @Test
    void aiReordersTheCandidatesAndAddsTheNote() {
        stockBoth();
        ai.reply("{\"ranking\": [{\"id\": \"L1\", \"reason\": \"Soup first\"}, {\"id\": \"S1\", \"reason\": \"Quick\"}]}");

        LeftoverFlow.Outcome outcome = rerank(LeftoverFlow.propose(planner, plan, REQUEST, TODAY));

        assertEquals(List.of("L1", "S1"), ids(outcome));
        assertEquals("Soup first", outcome.proposal().orElseThrow().assignments().get(0).reason());
        assertEquals(List.of(LeftoverFlow.AI_NOTE), outcome.notes());
        assertEquals("Ranked with AI assistance (it can only reorder these safe recipes).", LeftoverFlow.AI_NOTE);
        assertTrue(outcome.aiProblem().isEmpty());
        assertFalse(events.isEmpty());
    }

    @Test
    void aiFailureKeepsTheDeterministicProposalWithTheProblem() {
        stockBoth();
        ai.fail("The AI service had an internal error");

        LeftoverFlow.Outcome outcome = rerank(LeftoverFlow.propose(planner, plan, REQUEST, TODAY));

        assertEquals(List.of("S1", "L1"), ids(outcome));
        assertEquals("The AI service had an internal error", outcome.aiProblem().orElseThrow());
        assertTrue(outcome.notes().isEmpty());
    }

    @Test
    void disabledAiIsReportedAsAProblem() {
        stockBoth();
        llm.disable("Not configured");

        LeftoverFlow.Outcome outcome = rerank(LeftoverFlow.propose(planner, plan, REQUEST, TODAY));

        assertEquals("AI features are disabled: Not configured", outcome.aiProblem().orElseThrow());
        assertEquals(List.of("S1", "L1"), ids(outcome));
        assertTrue(ai.requests.isEmpty());
    }

    @Test
    void oneCandidateSkipsTheAi() {
        pantry.addItem(new PantryItem("Red lentils", 400, Unit.GRAM, TODAY.plusDays(1)));

        LeftoverFlow.Outcome outcome = rerank(LeftoverFlow.propose(planner, plan, REQUEST, TODAY));

        assertEquals(List.of("L1"), ids(outcome));
        assertEquals(List.of(LeftoverFlow.SINGLE_CANDIDATE_NOTE), outcome.notes());
        assertTrue(ai.requests.isEmpty());
    }

    @Test
    void anUnknownRecipeFromTheAiIsNeverAdded() {
        stockBoth();
        ai.reply("{\"ranking\": [{\"id\": \"R999\", \"reason\": \"Invented\"}]}",
                "{\"ranking\": [{\"id\": \"L1\", \"reason\": \"Soup first\"}]}");

        LeftoverFlow.Outcome outcome = rerank(LeftoverFlow.propose(planner, plan, REQUEST, TODAY));

        assertEquals(List.of("L1", "S1"), ids(outcome));
        assertEquals(2, ai.requests.size());
        assertTrue(outcome.proposal().orElseThrow().assignments().stream().allMatch(a -> book.contains(a.recipe().getId())));
    }

    @Test
    void sharedWording() {
        stockBoth();
        LeftoverProposal proposal = LeftoverFlow.propose(planner, plan, REQUEST, TODAY).proposal().orElseThrow();

        assertEquals(List.of("Expiring within 3 days: Spinach (expires today); Red lentils (expires in 1 day)",
                "Note: 2 meal(s) proposed for 2 expiring item(s).", "Note: extra"),
                LeftoverFlow.headerLines(proposal, REQUEST, List.of("extra")));
        assertEquals("Applied: Add 2 leftover meal(s) to the plan.", LeftoverFlow.appliedMessage("Add 2 leftover meal(s) to the plan"));
        assertEquals("Proposal (nothing applied yet):", LeftoverFlow.PROPOSAL_TITLE);
    }
}
