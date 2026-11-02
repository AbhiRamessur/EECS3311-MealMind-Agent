package com.mealmind.planning;

import static com.mealmind.planning.PlanningFixtures.CHICKEN_PLATE;
import static com.mealmind.planning.PlanningFixtures.RICE_BOWL;
import static com.mealmind.planning.PlanningFixtures.TOFU_STIR_FRY;
import static com.mealmind.planning.PlanningFixtures.grams;
import static com.mealmind.planning.PlanningFixtures.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningStrategiesTest {

    private final ScoringContext empty = new ScoringContext(new Pantry());

    @Test
    void allIsUnmodifiableAndInOrder() {
        List<PlanningStrategy> all = PlanningStrategies.all();

        assertEquals(List.of("pantry-first", "budget", "high-protein"), all.stream().map(PlanningStrategy::id).toList());
        assertEquals(List.of("Pantry-First", "Budget-Conscious", "High-Protein"),
                all.stream().map(PlanningStrategy::displayName).toList());
        assertThrows(UnsupportedOperationException.class, () -> all.add(new HighProteinStrategy()));
    }

    @Test
    void byIdIgnoresCaseAndWhitespace() {
        assertInstanceOf(PantryFirstStrategy.class, PlanningStrategies.byId(" Pantry-First "));
        assertInstanceOf(BudgetConsciousStrategy.class, PlanningStrategies.byId("BUDGET"));
        assertInstanceOf(HighProteinStrategy.class, PlanningStrategies.byId("high-protein"));
    }

    @Test
    void unknownIdListsTheAvailableIds() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PlanningStrategies.byId(" keto "));
        assertEquals("Unknown strategy 'keto'. Available: pantry-first, budget, high-protein", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PlanningStrategies.byId(""));
        assertThrows(IllegalArgumentException.class, () -> PlanningStrategies.byId(null));
    }

    @Test
    void idsAreKebabCaseAndGuidanceIsPlainNonBlankText() {
        for (PlanningStrategy strategy : PlanningStrategies.all()) {
            assertTrue(strategy.id().matches("[a-z]+(-[a-z]+)*"), strategy.id());
            assertFalse(strategy.promptGuidance().isBlank());
            assertTrue(strategy.promptGuidance().startsWith("Prefer "), strategy.promptGuidance());
            for (String forbidden : new String[] {"LLM_", "API key", "sk-", "http", "{", "}"}) {
                assertFalse(strategy.promptGuidance().contains(forbidden), forbidden);
            }
        }
    }

    @Test
    void pantryFirstScoreIsCoverageTimesHundredMinusMissing() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Rice", 500, Unit.GRAM));
        ScoringContext ctx = new ScoringContext(pantry);
        PantryFirstStrategy strategy = new PantryFirstStrategy();

        assertEquals(10_000, strategy.score(RICE_BOWL, ctx));
        assertEquals(4_999, strategy.score(TOFU_STIR_FRY, ctx));
        assertEquals(-1, strategy.score(CHICKEN_PLATE, ctx));
    }

    @Test
    void budgetScoreIsMinusCostPerServing() {
        assertEquals(-1.0, new BudgetConsciousStrategy().score(RICE_BOWL, empty));
        assertEquals(-6.0, new BudgetConsciousStrategy().score(CHICKEN_PLATE, empty));
    }

    @Test
    void highProteinScoreIsProteinPerServing() {
        assertEquals(40.0, new HighProteinStrategy().score(CHICKEN_PLATE, empty));
        assertEquals(25.0, new HighProteinStrategy().score(TOFU_STIR_FRY, empty));
    }

    @Test
    void scoringContextRequiresPantry() {
        assertThrows(NullPointerException.class, () -> new ScoringContext(null));
    }

    @Test
    void pantryFirstPrefersHigherCoverageThenFewerMissing() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Rice", 500, Unit.GRAM));
        pantry.addItem(new PantryItem("Beans", 500, Unit.GRAM));
        ScoringContext ctx = new ScoringContext(pantry);
        Recipe halfOneMissing = recipe("S1", "Half A", grams("Rice", 100, 0, 1), grams("Tofu", 100, 0, 1));
        Recipe halfTwoMissing = recipe("S2", "Half B", grams("Rice", 100, 0, 1), grams("Beans", 100, 0, 1),
                grams("Corn", 100, 0, 1), grams("Salsa", 100, 0, 1));
        PantryFirstStrategy strategy = new PantryFirstStrategy();

        assertTrue(strategy.score(RICE_BOWL, ctx) > strategy.score(halfOneMissing, ctx));
        assertTrue(strategy.score(halfOneMissing, ctx) > strategy.score(halfTwoMissing, ctx));
    }

    @Test
    void budgetPrefersCheaperAndHighProteinPrefersMoreProteinPerServing() {
        Recipe twoServings = new Recipe("S3", "Big Pot", 2, 10, List.of("Cook"), List.of(grams("Beans", 400, 60, 4.00)));

        // Per serving: Big Pot $2.00 / 30 g, Rice Bowl $1.00 / 5 g, Chicken Plate $6.00 / 40 g.
        assertTrue(new BudgetConsciousStrategy().score(RICE_BOWL, empty) > new BudgetConsciousStrategy().score(twoServings, empty));
        assertTrue(new BudgetConsciousStrategy().score(twoServings, empty) > new BudgetConsciousStrategy().score(CHICKEN_PLATE, empty));
        assertTrue(new HighProteinStrategy().score(CHICKEN_PLATE, empty) > new HighProteinStrategy().score(twoServings, empty));
        assertTrue(new HighProteinStrategy().score(twoServings, empty) > new HighProteinStrategy().score(RICE_BOWL, empty));
    }
}
