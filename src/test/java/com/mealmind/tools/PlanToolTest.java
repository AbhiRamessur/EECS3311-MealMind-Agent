package com.mealmind.tools;

import static com.mealmind.tools.ToolTestSupport.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** plan_show reads the current plan through a Supplier on every call. */
class PlanToolTest {

    private static Recipe recipe(String id, double calories, double cost) {
        Ingredient food = new Ingredient("Food " + id, 100, Unit.GRAM, calories, 10, 20, 5, cost);
        return new Recipe(id, "Recipe " + id, 2, 15, List.of("Cook"), List.of(food));
    }

    @Test
    void emptyPlanHasAllSlotsEmptyAndZeroTotals() {
        JsonNode result = ok(new PlanTool(() -> new MealPlan("Week", 2)), "{}");

        assertEquals("Week", result.get("name").asText());
        assertEquals(2, result.get("days").asInt());
        assertEquals(0, result.get("slots").size());
        assertEquals(8, result.get("emptySlots").size());
        assertEquals("BREAKFAST", result.get("emptySlots").get(0).get("slot").asText());
        assertEquals(0.0, result.get("totals").get("calories").asDouble());
        assertEquals(0.0, result.get("totals").get("cost").asDouble());
    }

    @Test
    void planWithTwoMeals() {
        MealPlan plan = new MealPlan("Week", 2);
        plan.setMeal(2, MealType.DINNER, new Meal(MealType.DINNER, "Pasta night", List.of(recipe("R1", 800, 6.00))));
        plan.setMeal(1, MealType.LUNCH, new Meal(MealType.LUNCH, null, List.of(recipe("R2", 400, 2.50))));

        JsonNode result = ok(new PlanTool(() -> plan), "{}");

        JsonNode slots = result.get("slots");
        assertEquals(2, slots.size());
        assertEquals(1, slots.get(0).get("day").asInt());
        assertEquals("LUNCH", slots.get(0).get("slot").asText());
        assertEquals("Lunch", slots.get(0).get("meal").asText());
        assertEquals("Pasta night", slots.get(1).get("meal").asText());
        assertEquals("R1", slots.get(1).get("recipes").get(0).get("id").asText());
        assertEquals(2, slots.get(1).get("recipes").get(0).get("servings").asInt());
        assertEquals(6, result.get("emptySlots").size());
        assertEquals(1200.0, result.get("totals").get("calories").asDouble());
        assertEquals(8.5, result.get("totals").get("cost").asDouble());
    }

    @Test
    void planSupplierIsReadOnEveryCall() {
        AtomicReference<MealPlan> holder = new AtomicReference<>(new MealPlan("Week", 7));
        PlanTool tool = new PlanTool(holder::get);
        assertEquals(7, ok(tool, "{}").get("days").asInt());

        holder.set(new MealPlan("Weekly Plan", 3));

        assertEquals(3, ok(tool, "{}").get("days").asInt());
    }
}
