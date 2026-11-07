package com.mealmind.nutrition;

import com.mealmind.model.DayPlan;
import com.mealmind.model.Ingredient;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.profile.UserProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Computes a {@link NutritionSummary} (the Stage 1 {@code NutritionAggregator.aggregateMacros()}). It is a
 * client of the Composite: every figure comes from {@code getCalories()}, {@code getProteinG()} and so on,
 * called on days and on the plan, which sum their children themselves. Nothing here is estimated by AI.
 */
public final class NutritionAggregator {

    private NutritionAggregator() {
    }

    /**
     * Analyzes a plan against a profile's targets.
     *
     * @param plan    the plan to analyze
     * @param profile whose calorie and macro targets to compare with
     * @return the summary
     * @throws NullPointerException if an argument is null
     */
    public static NutritionSummary aggregate(MealPlan plan, UserProfile profile) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(profile, "profile");
        List<DayNutrition> days = new ArrayList<>();
        int mealsPlanned = 0;
        for (DayPlan day : plan.getDays()) {
            int filled = day.getFilledSlots().size();
            mealsPlanned += filled;
            if (filled > 0) {
                days.add(new DayNutrition(day.getDayNumber(), Macros.of(day), filled));
            }
        }
        Macros totals = Macros.of(plan);
        Macros average = days.isEmpty() ? Macros.ZERO : totals.dividedBy(days.size());
        return new NutritionSummary(days, totals, average, progress(average, profile), unknownIngredients(plan),
                mealsPlanned, plan.getNumberOfDays() * MealType.values().length);
    }

    /**
     * The Stage 1 name for {@link #aggregate}.
     *
     * @param plan    the plan to analyze
     * @param profile whose targets to compare with
     * @return the summary
     */
    public static NutritionSummary aggregateMacros(MealPlan plan, UserProfile profile) {
        return aggregate(plan, profile);
    }

    private static List<TargetProgress> progress(Macros average, UserProfile profile) {
        List<TargetProgress> progress = new ArrayList<>();
        profile.getCalorieTarget().ifPresent(t -> progress.add(TargetProgress.of("Calories", average.calories(), t)));
        profile.getProteinTargetG().ifPresent(t -> progress.add(TargetProgress.of("Protein", average.proteinG(), t)));
        profile.getCarbsTargetG().ifPresent(t -> progress.add(TargetProgress.of("Carbs", average.carbsG(), t)));
        profile.getFatTargetG().ifPresent(t -> progress.add(TargetProgress.of("Fat", average.fatG(), t)));
        return progress;
    }

    /** Distinct names (ignoring case, first spelling kept), sorted ignoring case. */
    private static List<String> unknownIngredients(MealPlan plan) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Ingredient ingredient : plan.getIngredients()) {
            if (NutritionData.isUnknown(ingredient)) {
                byKey.putIfAbsent(ingredient.getName().toLowerCase(Locale.ROOT), ingredient.getName());
            }
        }
        return byKey.values().stream().sorted(Comparator.comparing((String n) -> n.toLowerCase(Locale.ROOT))).toList();
    }
}
