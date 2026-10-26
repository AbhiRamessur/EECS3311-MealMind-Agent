package com.mealmind.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.command.AddPantryItemCommand;
import com.mealmind.command.CommandHistory;
import com.mealmind.model.ChangeEvent;
import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShoppingListGeneratorObserverTest {

    private MealPlan plan;
    private Pantry pantry;
    private ShoppingListGenerator generator;
    private List<ChangeEvent> events;

    private static Meal riceMeal(MealType type) {
        Ingredient rice = new Ingredient("Rice", 200, Unit.GRAM, 260, 5, 56, 1, 0.50);
        return new Meal(type, null, List.of(rice));
    }

    private static Meal chickenMeal(MealType type) {
        Ingredient chicken = new Ingredient("Chicken Breast", 300, Unit.GRAM, 400, 90, 0, 9, 4.50);
        return new Meal(type, null, List.of(chicken));
    }

    @BeforeEach
    void setUp() {
        plan = new MealPlan("Week", 2);
        plan.setMeal(1, MealType.DINNER, riceMeal(MealType.DINNER));
        pantry = new Pantry();
        generator = new ShoppingListGenerator(plan, pantry);
        events = new ArrayList<>();
        generator.addObserver(events::add);
    }

    @Test
    void initialListIsComputedWithoutNotification() {
        ShoppingListGenerator fresh = new ShoppingListGenerator(plan, pantry);

        assertEquals(1, fresh.getCurrent().size());
        assertEquals(0, events.size());
    }

    @Test
    void pantryAddRecomputesAndNotifiesOnce() {
        pantry.addItem(new PantryItem("Rice", 150, Unit.GRAM));

        assertEquals(1, events.size());
        assertSame(generator, events.get(0).source());
        assertEquals("Shopping list updated (1 items)", events.get(0).description());
        assertEquals(50, generator.getCurrent().getItems().get(0).getQuantity(), 1e-9);
    }

    @Test
    void planSetMealRecomputesAndNotifiesOnce() {
        plan.setMeal(2, MealType.LUNCH, chickenMeal(MealType.LUNCH));

        assertEquals(1, events.size());
        assertEquals(2, generator.getCurrent().size());
    }

    @Test
    void changeThroughGetDayNotifiesOnce() {
        plan.getDay(1).setMeal(MealType.LUNCH, chickenMeal(MealType.LUNCH));

        assertEquals(1, events.size());
        assertEquals(2, generator.getCurrent().size());
    }

    @Test
    void failedPantryAddDoesNotNotify() {
        pantry.addItem(new PantryItem("Rice", 100, Unit.GRAM));
        events.clear();

        assertThrows(IncompatibleUnitException.class, () -> pantry.addItem(new PantryItem("Rice", 1, Unit.LITER)));

        assertEquals(0, events.size());
    }

    @Test
    void detachStopsUpdatesAndIsSafeTwice() {
        ShoppingList before = generator.getCurrent();
        generator.detach();
        generator.detach();

        pantry.addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));
        plan.setMeal(2, MealType.LUNCH, chickenMeal(MealType.LUNCH));

        assertSame(before, generator.getCurrent());
        assertEquals(0, events.size());
        assertEquals(0, pantry.observerCount());
        assertEquals(0, plan.observerCount());
    }

    @Test
    void twoGeneratorsOnOnePlanWorkIndependently() {
        Pantry otherPantry = new Pantry();
        otherPantry.addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));
        ShoppingListGenerator other = new ShoppingListGenerator(plan, otherPantry);

        assertEquals(1, generator.getCurrent().size());
        assertTrue(other.getCurrent().isEmpty());

        plan.setMeal(2, MealType.LUNCH, chickenMeal(MealType.LUNCH));

        assertEquals(2, generator.getCurrent().size());
        assertEquals(1, other.getCurrent().size());
    }

    @Test
    void commandExecuteAndUndoBothUpdateTheList() {
        CommandHistory history = new CommandHistory();

        history.pushAndExecute(new AddPantryItemCommand(pantry, new PantryItem("Rice", 200, Unit.GRAM)));
        assertTrue(generator.getCurrent().isEmpty());

        history.undo();
        assertEquals(1, generator.getCurrent().size());
        assertEquals(2, events.size());
    }
}
