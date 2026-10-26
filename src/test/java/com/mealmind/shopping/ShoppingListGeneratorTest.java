package com.mealmind.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.ChangeEvent;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShoppingListGeneratorTest {

    private static final String PANTRY_EMPTY = "Pantry is empty: all ingredients are listed as missing.";
    private static final String NOTHING_TO_BUY = "Meal plan has no meals; nothing to buy.";
    private static final String SEVERAL_UNITS = "Needed in several incompatible units; listed separately";

    /** Chicken: 300 g, 400 kcal, 4.50. */
    private static Ingredient chicken(double grams) {
        double factor = grams / 300;
        return new Ingredient("Chicken Breast", grams, Unit.GRAM, 400 * factor, 90 * factor, 0, 9 * factor, 4.50 * factor);
    }

    /** Rice: 200 g, 0.50. */
    private static Ingredient rice() {
        return new Ingredient("Rice", 200, Unit.GRAM, 260, 5, 56, 1, 0.50);
    }

    private static Ingredient ingredient(String name, double quantity, Unit unit, double cost) {
        return new Ingredient(name, quantity, unit, 100, 1, 1, 1, cost);
    }

    private static ShoppingList compare(Pantry pantry, Ingredient... required) {
        return ShoppingListGenerator.compareAgainstPantry(List.of(required), pantry);
    }

    private static ShoppingItem only(ShoppingList list) {
        assertEquals(1, list.size(), list.toString());
        return list.getItems().get(0);
    }

    @Test
    void emptyPantryListsEverythingWithWarningAndPlanCost() {
        MealPlan plan = new MealPlan("Week", 2);
        plan.setMeal(1, MealType.LUNCH, new Meal(MealType.LUNCH, null, List.of(chicken(300), rice())));
        plan.setMeal(2, MealType.DINNER, new Meal(MealType.DINNER, null, List.of(rice())));

        ShoppingList list = new ShoppingListGenerator(plan, new Pantry()).getCurrent();

        assertEquals(2, list.size());
        assertEquals(List.of(PANTRY_EMPTY), list.getWarnings());
        assertEquals(plan.getCost(), list.getTotalEstimatedCost(), 1e-9);
        assertEquals(400, list.getItems().get(1).getQuantity(), 1e-9);
    }

    @Test
    void sameIngredientIsMergedAcrossMeals() {
        ShoppingItem item = only(compare(new Pantry(), chicken(300), chicken(500)));

        assertEquals(800, item.getQuantity(), 1e-9);
        assertEquals(12.00, item.getEstimatedCost(), 1e-9);
        assertEquals(Aisle.MEAT_SEAFOOD, item.getAisle());
    }

    @Test
    void mixedUnitsAreMergedIntoFirstSeenUnit() {
        Ingredient grams = ingredient("Flour", 500, Unit.GRAM, 1.00);
        Ingredient kilos = ingredient("flour", 1, Unit.KILOGRAM, 2.00);

        ShoppingItem item = only(compare(new Pantry(), grams, kilos));

        assertEquals(1500, item.getQuantity(), 1e-9);
        assertEquals(Unit.GRAM, item.getUnit());
        assertEquals(3.00, item.getEstimatedCost(), 1e-9);
    }

    @Test
    void partialPantryStockLeavesProportionalCost() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Chicken Breast", 300, Unit.GRAM));

        ShoppingItem item = only(compare(pantry, chicken(300), chicken(500)));

        assertEquals(500, item.getQuantity(), 1e-9);
        assertEquals(12.00 * 5 / 8, item.getEstimatedCost(), 1e-9);
    }

    @Test
    void fullyCoveredRequirementsProduceNoItem() {
        Pantry exact = new Pantry();
        exact.addItem(new PantryItem("Chicken Breast", 800, Unit.GRAM));
        assertTrue(compare(exact, chicken(300), chicken(500)).isEmpty());

        Pantry larger = new Pantry();
        larger.addItem(new PantryItem("Chicken Breast", 1, Unit.KILOGRAM));
        assertTrue(compare(larger, chicken(500)).isEmpty());
    }

    @Test
    void namesMatchIgnoringCaseAndSpacing() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("  CHICKEN   breast ", 500, Unit.GRAM));

        assertTrue(compare(pantry, chicken(500)).isEmpty());
    }

    @Test
    void incompatiblePantryUnitListsFullAmountWithNote() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Flour", 1, Unit.LITER));

        ShoppingItem item = only(compare(pantry, ingredient("Flour", 500, Unit.GRAM, 1.00)));

        assertEquals(500, item.getQuantity(), 1e-9);
        assertEquals(1.00, item.getEstimatedCost(), 1e-9);
        assertEquals(Optional.of("Pantry stock of Flour is in a unit that cannot be compared (l); full amount listed"),
                item.getNote());
    }

    @Test
    void nameNeededInTwoDimensionsGivesTwoNotedItems() {
        ShoppingList list = compare(new Pantry(),
                ingredient("Egg", 3, Unit.COUNT, 1.20), ingredient("Egg", 100, Unit.GRAM, 0.80));

        assertEquals(2, list.size());
        for (ShoppingItem item : list.getItems()) {
            assertEquals(Optional.of(SEVERAL_UNITS), item.getNote());
        }
        assertEquals(List.of(Unit.GRAM, Unit.COUNT), list.getItems().stream().map(ShoppingItem::getUnit).toList());
    }

    @Test
    void notesAreCombinedWithSemicolon() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Egg", 1, Unit.LITER));

        ShoppingList list = compare(pantry, ingredient("Egg", 3, Unit.COUNT, 1.20), ingredient("Egg", 100, Unit.GRAM, 0.80));

        assertEquals(Optional.of("Pantry stock of Egg is in a unit that cannot be compared (l); full amount listed; "
                + SEVERAL_UNITS), list.getItems().get(0).getNote());
    }

    @Test
    void zeroQuantityIngredientIsIgnored() {
        ShoppingList list = compare(new Pantry(), ingredient("Water", 0, Unit.MILLILITER, 0), rice());

        assertEquals(List.of("Rice"), list.getItems().stream().map(ShoppingItem::getName).toList());
    }

    @Test
    void emptyPlanGivesEmptyListWithWarning() {
        ShoppingList list = new ShoppingListGenerator(new MealPlan("Week", 1), new Pantry()).getCurrent();

        assertTrue(list.isEmpty());
        assertEquals(List.of(NOTHING_TO_BUY), list.getWarnings());
    }

    @Test
    void firstSeenNameAndUnitAreUsed() {
        Ingredient first = ingredient("Chicken Breast", 300, Unit.GRAM, 4.50);
        Ingredient second = ingredient("chicken breast", 1, Unit.KILOGRAM, 15.00);

        ShoppingItem item = only(compare(new Pantry(), first, second));

        assertEquals("Chicken Breast", item.getName());
        assertEquals(Unit.GRAM, item.getUnit());
    }

    @Test
    void generateDoesNotNotifyOrChangeCache() {
        MealPlan plan = new MealPlan("Week", 1);
        plan.setMeal(1, MealType.DINNER, new Meal(MealType.DINNER, null, List.of(rice())));
        Pantry pantry = new Pantry();
        ShoppingListGenerator generator = new ShoppingListGenerator(plan, pantry);
        List<ChangeEvent> events = new ArrayList<>();
        generator.addObserver(events::add);
        ShoppingList cached = generator.getCurrent();
        generator.detach();
        pantry.addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));

        ShoppingList fresh = generator.generate();

        assertTrue(fresh.isEmpty());
        assertEquals(1, generator.getCurrent().size());
        assertEquals(cached, generator.getCurrent());
        assertEquals(0, events.size());
    }
}
