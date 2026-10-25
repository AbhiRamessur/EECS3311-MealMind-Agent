package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Drives all three receivers through one {@link CommandHistory}. */
class CommandIntegrationTest {

    @Test
    void undoAllRestoresInitialStateAndRedoAllRestoresFinalState() {
        Pantry pantry = new Pantry();
        PantryItem rice = new PantryItem("Rice", 500, Unit.GRAM);
        pantry.addItem(rice);

        Ingredient beans = new Ingredient("Black beans", 200, Unit.GRAM, 260, 17, 46, 1, 1.20);
        Meal curry = new Meal(MealType.DINNER, "Curry", List.of(beans));
        Meal pasta = new Meal(MealType.DINNER, "Pasta", List.of(beans));
        MealPlan plan = new MealPlan("Week", 2);
        plan.setMeal(1, MealType.DINNER, curry);

        RecipeBook book = new RecipeBook();
        Recipe chili = new Recipe("chili", "Chili", 4, 40, List.of("Simmer"), List.of(beans));
        book.put(chili);

        CommandHistory history = new CommandHistory();
        history.pushAndExecute(new AddPantryItemCommand(pantry, new PantryItem("rice", 1, Unit.KILOGRAM)));
        history.pushAndExecute(new ReplaceMealCommand(plan, 1, MealType.DINNER, pasta));
        history.pushAndExecute(new ScaleRecipeCommand(book, "chili", 8));

        assertTrue(history.undo());
        assertTrue(history.undo());
        assertTrue(history.undo());
        assertFalse(history.canUndo());

        assertEquals(List.of(rice), pantry.getItems());
        assertSame(curry, plan.getMeal(1, MealType.DINNER).orElseThrow());
        assertSame(chili, book.get("chili").orElseThrow());

        assertTrue(history.redo());
        assertTrue(history.redo());
        assertTrue(history.redo());
        assertFalse(history.canRedo());

        assertEquals(1500, pantry.getItem("rice").orElseThrow().getQuantity(), 1e-9);
        assertSame(pasta, plan.getMeal(1, MealType.DINNER).orElseThrow());
        assertEquals(8, book.get("chili").orElseThrow().getServings());
        assertEquals(400, book.get("chili").orElseThrow().getIngredients().get(0).getQuantity(), 1e-9);
    }
}
