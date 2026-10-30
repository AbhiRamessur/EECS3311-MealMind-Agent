package com.mealmind.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mealmind.model.DayPlan;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code plan_show}: the current meal plan, its empty slots and its totals. The plan is fetched from
 * the supplier on every call, so a replaced plan ({@code plan new}) is always reflected. Read-only.
 */
public final class PlanTool implements Tool {

    private static final ToolSpec SPEC = new ToolSpec("plan_show",
            "Shows the current meal plan: filled slots with their recipes, empty slots, and plan totals.", List.of());

    private final Supplier<MealPlan> plan;

    /**
     * Creates the tool.
     *
     * @param plan supplies the current plan on each call
     * @throws NullPointerException if {@code plan} is null
     */
    public PlanTool(Supplier<MealPlan> plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        MealPlan current = plan.get();
        ObjectNode result = ToolJson.MAPPER.createObjectNode();
        result.put("name", current.getName());
        result.put("days", current.getNumberOfDays());
        ArrayNode slots = result.putArray("slots");
        for (DayPlan day : current.getDays()) {
            for (MealType type : MealType.values()) {
                Optional<Meal> meal = day.getMeal(type);
                if (meal.isEmpty()) {
                    continue;
                }
                ObjectNode slot = slots.addObject();
                slot.put("day", day.getDayNumber());
                slot.put("slot", type.name());
                slot.put("meal", meal.get().getName());
                ArrayNode recipes = slot.putArray("recipes");
                for (Recipe recipe : meal.get().getRecipes()) {
                    recipes.addObject().put("id", recipe.getId()).put("name", recipe.getName())
                            .put("servings", recipe.getServings());
                }
            }
        }
        ArrayNode empty = result.putArray("emptySlots");
        List<MealPlan.Slot> emptySlots = current.getEmptySlots();
        emptySlots.forEach(slot -> empty.addObject().put("day", slot.day()).put("slot", slot.type().name()));
        result.set("totals", ToolJson.nutrition(current, 1));
        return result;
    }
}
