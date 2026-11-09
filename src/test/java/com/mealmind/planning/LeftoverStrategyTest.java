package com.mealmind.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.leftovers.ExpiringItem;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeftoverStrategyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private static ExpiringItem item(String name, long days) {
        return ExpiringItem.of(new PantryItem(name, 100, Unit.GRAM, TODAY.plusDays(days)), TODAY);
    }

    private static Recipe recipe(String id, String... ingredients) {
        return new Recipe(id, "Recipe " + id, 1, 10, List.of("Cook"), java.util.Arrays.stream(ingredients)
                .map(name -> new Ingredient(name, 100, Unit.GRAM, 100, 10, 10, 5, 1.00)).toList());
    }

    @Test
    void matchesEqualNamesAndWholeWordRunsBothWays() {
        assertTrue(LeftoverStrategy.matches("red lentils", "red lentils"));
        assertTrue(LeftoverStrategy.matches("red lentils", "lentils"));
        assertTrue(LeftoverStrategy.matches("lentils", "red lentils"));
        assertTrue(LeftoverStrategy.matches("baby spinach leaves", "spinach"));
        assertTrue(LeftoverStrategy.matches("salt, to taste", "salt"));
    }

    @Test
    void matchingIsWholeWordAndNeedsThreeCharacters() {
        assertFalse(LeftoverStrategy.matches("boat", "oat"));
        assertFalse(LeftoverStrategy.matches("rolled oats", "oat"));
        assertFalse(LeftoverStrategy.matches("red bell pepper", "red pepper"));
        assertFalse(LeftoverStrategy.matches("ox", "ox"));
        assertFalse(LeftoverStrategy.matches("ox tail", "ox"));
        assertFalse(LeftoverStrategy.matches("milk", "yogurt"));
    }

    @Test
    void usedListsTheMatchedItemsInGivenOrder() {
        ExpiringItem lentils = item("Red lentils", 1);
        ExpiringItem carrots = item("Carrots", 2);
        ExpiringItem milk = item("Milk", 0);
        LeftoverStrategy strategy = new LeftoverStrategy(List.of(milk, lentils, carrots));

        assertEquals(List.of(lentils, carrots), strategy.used(recipe("A", "Red lentils", "Carrots", "Onion")));
        assertEquals(List.of(), strategy.used(recipe("B", "Rice")));
    }

    @Test
    void moreUrgentItemsScoreHigherAndCoverageBreaksTies() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Onion", 500, Unit.GRAM));
        ScoringContext ctx = new ScoringContext(pantry);
        LeftoverStrategy strategy = new LeftoverStrategy(List.of(item("Milk", 0), item("Carrots", 3)));

        double urgent = strategy.score(recipe("A", "Milk", "Rice"), ctx);
        double later = strategy.score(recipe("B", "Carrots", "Rice"), ctx);
        double laterCovered = strategy.score(recipe("C", "Carrots", "Onion"), ctx);

        assertEquals(400.0, urgent);
        assertEquals(100.0, later);
        assertEquals(150.0, laterCovered);
        assertTrue(urgent > laterCovered && laterCovered > later);
    }

    @Test
    void identityAndGuidance() {
        LeftoverStrategy strategy = new LeftoverStrategy(List.of());
        assertEquals("leftovers", strategy.id());
        assertEquals("Use Expiring Items", strategy.displayName());
        assertFalse(strategy.promptGuidance().isBlank());
    }

    @Test
    void registeredStrategiesAreUnchanged() {
        assertEquals(List.of("pantry-first", "budget", "high-protein"),
                PlanningStrategies.all().stream().map(PlanningStrategy::id).toList());
    }
}
