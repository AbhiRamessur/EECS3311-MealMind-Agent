package com.mealmind.planning;

import static com.mealmind.planning.PlanningFixtures.grams;
import static com.mealmind.planning.PlanningFixtures.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PantryCoverageTest {

    /** Four ingredients in the order Rice, Beans, Corn, Salsa. */
    private static final Recipe BOWL = recipe("C1", "Burrito Bowl", grams("Rice", 100, 0, 1), grams("Beans", 200, 0, 1),
            grams("Corn", 50, 0, 1), grams("Salsa", 40, 0, 1));

    private Pantry pantry;

    @BeforeEach
    void setUp() {
        pantry = new Pantry();
    }

    @Test
    void fullCoverage() {
        pantry.addItem(new PantryItem("Rice", 100, Unit.GRAM));
        pantry.addItem(new PantryItem("Beans", 200, Unit.GRAM));
        pantry.addItem(new PantryItem("Corn", 50, Unit.GRAM));
        pantry.addItem(new PantryItem("Salsa", 40, Unit.GRAM));

        assertEquals(new PantryCoverage.Result(100, List.of()), PantryCoverage.of(BOWL, pantry));
    }

    @Test
    void partialCoverageRoundsToNearestPercent() {
        pantry.addItem(new PantryItem("Beans", 200, Unit.GRAM));
        Recipe three = recipe("C2", "Three", grams("Rice", 100, 0, 1), grams("Beans", 200, 0, 1), grams("Corn", 50, 0, 1));

        assertEquals(new PantryCoverage.Result(25, List.of("Rice", "Corn", "Salsa")), PantryCoverage.of(BOWL, pantry));
        assertEquals(33, PantryCoverage.of(three, pantry).percent());
    }

    @Test
    void noCoverageListsEveryIngredientInRecipeOrder() {
        assertEquals(new PantryCoverage.Result(0, List.of("Rice", "Beans", "Corn", "Salsa")), PantryCoverage.of(BOWL, pantry));
    }

    @Test
    void tooLittleIsNotCovered() {
        pantry.addItem(new PantryItem("Rice", 99, Unit.GRAM));

        assertEquals(List.of("Rice", "Beans", "Corn", "Salsa"), PantryCoverage.of(BOWL, pantry).missing());
    }

    @Test
    void incompatibleUnitCountsAsNotCovered() {
        pantry.addItem(new PantryItem("Rice", 2, Unit.CUP));

        assertEquals(0, PantryCoverage.of(BOWL, pantry).percent());
        assertEquals("Rice", PantryCoverage.of(BOWL, pantry).missing().get(0));
    }

    @Test
    void largerPantryAndConvertibleUnitsAreCovered() {
        pantry.addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));
        pantry.addItem(new PantryItem("Beans", 5000, Unit.GRAM));

        assertEquals(new PantryCoverage.Result(50, List.of("Corn", "Salsa")), PantryCoverage.of(BOWL, pantry));
    }

    @Test
    void resultListIsUnmodifiableAndArgumentsAreRequired() {
        PantryCoverage.Result result = PantryCoverage.of(BOWL, pantry);

        assertThrows(UnsupportedOperationException.class, () -> result.missing().add("x"));
        assertThrows(NullPointerException.class, () -> PantryCoverage.of(null, pantry));
        assertThrows(NullPointerException.class, () -> PantryCoverage.of(BOWL, null));
    }
}
