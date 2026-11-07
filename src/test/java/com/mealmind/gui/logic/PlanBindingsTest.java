package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.agent.PlanMeals;
import com.mealmind.app.ApplicationContext;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanBindingsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void repointFollowsTheReplacedPlanAndGenerator() {
        ApplicationContext ctx = ApplicationContext.create(dir, CLOCK, warning -> { });
        int[] planChanges = {0};
        int[] shoppingChanges = {0};
        PlanBindings bindings = new PlanBindings(ctx, new ImmediateUiThread());
        bindings.onPlanChange(() -> planChanges[0]++);
        bindings.onShoppingChange(() -> shoppingChanges[0]++);
        MealPlan old = ctx.plan();

        ctx.newPlan(2);
        old.setMeal(1, MealType.DINNER, PlanMeals.forRecipe(MealType.DINNER, ctx.recipeBook().get("R103").orElseThrow()));
        int beforeRepoint = planChanges[0];
        bindings.repoint();
        assertEquals(beforeRepoint + 1, planChanges[0], "repoint runs each action once");

        int planBefore = planChanges[0];
        int shoppingBefore = shoppingChanges[0];
        ctx.plan().setMeal(1, MealType.LUNCH, PlanMeals.forRecipe(MealType.LUNCH, ctx.recipeBook().get("R103").orElseThrow()));
        assertEquals(planBefore + 1, planChanges[0]);
        assertEquals(shoppingBefore + 1, shoppingChanges[0]);

        old.clearMeal(1, MealType.DINNER);
        assertEquals(planBefore + 1, planChanges[0], "the old plan is no longer watched");
        assertEquals(2, bindings.size());

        bindings.close();
        ctx.plan().clearMeal(1, MealType.LUNCH);
        assertEquals(planBefore + 1, planChanges[0]);
        assertEquals(0, bindings.size());
    }
}
