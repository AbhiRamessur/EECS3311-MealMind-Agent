package com.mealmind.validation;

import com.mealmind.model.DayPlan;
import com.mealmind.model.Ingredient;
import com.mealmind.model.MealComponent;
import com.mealmind.model.MealPlan;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Checks meals and plans against the user's {@link UserProfile} (the Stage 1 {@code ConstraintValidator}).
 *
 * <p><strong>What it is and is not.</strong> This is a deterministic, conservative keyword check on
 * ingredient <em>names</em> (see {@link FoodKeywords} for the lists and matching rules). It prefers
 * false positives to false negatives. It cannot see hidden ingredients, for example dairy in a sauce or
 * gluten in a stock whose name does not say so, and it is not medical advice.
 *
 * <p>Allergen and diet conflicts are {@link Severity#SAFETY} violations, which callers must treat as
 * blocking. Calorie, macro and budget checks are {@link Severity#ADVISORY}: they are warnings only.
 *
 * <p>The profile is read on every call, so profile edits take effect immediately. Any
 * {@link MealComponent} can be checked (an ingredient, recipe, meal, day or whole plan) because the
 * check walks {@link MealComponent#getIngredients()} (Composite pattern).
 */
public final class ConstraintValidator {

    /** Advisory thresholds, as fractions of the target. */
    private static final double CALORIE_LIMIT = 1.10;
    private static final double PROTEIN_MINIMUM = 0.80;
    private static final double MACRO_LIMIT = 1.25;
    /** Protein is only judged on days with at least this many filled slots. */
    private static final int PROTEIN_MIN_FILLED_SLOTS = 3;
    private static final double EPSILON = 1e-9;
    private static final int DAYS_PER_WEEK = 7;

    private final UserProfile profile;

    /**
     * Creates a validator for a profile.
     *
     * @param profile the profile to check against (read live on every call)
     * @throws NullPointerException if {@code profile} is null
     */
    public ConstraintValidator(UserProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * Checks every ingredient beneath {@code component} for listed allergens and diet conflicts. An
     * ingredient that breaks several rules gets one violation per rule; identical violations (the same
     * ingredient used twice) are reported once.
     *
     * @param component an ingredient, recipe, meal, day or plan
     * @return the {@link Severity#SAFETY} violations found, or a clean result
     * @throws NullPointerException if {@code component} is null
     */
    public ValidationResult validateAgainstBlacklists(MealComponent component) {
        Objects.requireNonNull(component, "component");
        Set<String> allergens = profile.getAllergens();
        DietType diet = profile.getDiet();
        Set<Violation> found = new LinkedHashSet<>();
        for (Ingredient ingredient : component.getIngredients()) {
            String name = ingredient.getName();
            for (String allergen : allergens) {
                FoodKeywords.findAllergen(allergen, name).ifPresent(term ->
                        found.add(new Violation(Severity.SAFETY, ViolationType.ALLERGEN, name,
                                allergenMessage(name, term, allergen))));
            }
            FoodKeywords.findDietConflict(diet, name).ifPresent(term ->
                    found.add(new Violation(Severity.SAFETY, ViolationType.DIET, name,
                            name + " is not allowed for a " + diet.getDisplayName().toLowerCase(Locale.ROOT)
                                    + " diet (contains " + term + ")")));
        }
        return new ValidationResult(new ArrayList<>(found));
    }

    private static String allergenMessage(String ingredient, String term, String allergen) {
        return term.equals(allergen)
                ? ingredient + " contains " + term + ", a listed allergen"
                : ingredient + " contains " + term + " (listed allergen: " + allergen + ")";
    }

    /**
     * Checks a whole plan: the allergen and diet checks of {@link #validateAgainstBlacklists}, plus
     * advisories for each day that has at least one meal (empty days are skipped) and for the budget:
     * <ul>
     *   <li>calories above 110% of the calorie target;</li>
     *   <li>protein below 80% of the protein target, only on days with at least 3 filled slots;</li>
     *   <li>carbs or fat above 125% of their targets;</li>
     *   <li>plan cost above the weekly budget prorated to the plan length ({@code budget * days / 7}).</li>
     * </ul>
     * A target that is not set skips its check.
     *
     * @param plan the plan to check
     * @return safety violations followed by advisories
     * @throws NullPointerException if {@code plan} is null
     */
    public ValidationResult validateMacrosAndAllergens(MealPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<Violation> advisories = new ArrayList<>();
        for (DayPlan day : plan.getDays()) {
            if (!day.getFilledSlots().isEmpty()) {
                checkDay(day, advisories);
            }
        }
        profile.getWeeklyBudget().ifPresent(budget -> {
            double allowed = budget * plan.getNumberOfDays() / DAYS_PER_WEEK;
            if (plan.getCost() > allowed + EPSILON) {
                advisories.add(advisory(ViolationType.BUDGET, "Plan", "Plan cost $" + format(plan.getCost())
                        + " is above your budget of $" + format(allowed) + " for " + plan.getNumberOfDays() + " days"));
            }
        });
        return validateAgainstBlacklists(plan).merge(new ValidationResult(advisories));
    }

    private void checkDay(DayPlan day, List<Violation> advisories) {
        String subject = day.getName();
        Optional<Integer> calories = profile.getCalorieTarget();
        if (calories.isPresent() && day.getCalories() > calories.get() * CALORIE_LIMIT + EPSILON) {
            advisories.add(advisory(ViolationType.CALORIES, subject, subject + ": " + format(day.getCalories())
                    + " kcal is above 110% of your " + calories.get() + " kcal target"));
        }
        Optional<Double> protein = profile.getProteinTargetG();
        if (protein.isPresent() && day.getFilledSlots().size() >= PROTEIN_MIN_FILLED_SLOTS
                && day.getProteinG() < protein.get() * PROTEIN_MINIMUM - EPSILON) {
            advisories.add(advisory(ViolationType.PROTEIN, subject, subject + ": " + format(day.getProteinG())
                    + " g protein is below 80% of your " + format(protein.get()) + " g target"));
        }
        checkUpperLimit(ViolationType.CARBS, "carbs", day.getCarbsG(), profile.getCarbsTargetG(), subject, advisories);
        checkUpperLimit(ViolationType.FAT, "fat", day.getFatG(), profile.getFatTargetG(), subject, advisories);
    }

    private static void checkUpperLimit(ViolationType type, String label, double actual, Optional<Double> target,
                                        String subject, List<Violation> advisories) {
        if (target.isPresent() && actual > target.get() * MACRO_LIMIT + EPSILON) {
            advisories.add(advisory(type, subject, subject + ": " + format(actual) + " g " + label
                    + " is above 125% of your " + format(target.get()) + " g target"));
        }
    }

    private static Violation advisory(ViolationType type, String subject, String message) {
        return new Violation(Severity.ADVISORY, type, subject, message);
    }

    /** At most one decimal, without a trailing ".0" (e.g. 2450, 87.5). */
    private static String format(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * Returns whether {@code component} has no allergen or diet conflicts.
     *
     * @param component what to check
     * @return {@code true} if {@link #validateAgainstBlacklists} finds no safety violations
     */
    public boolean isSafe(MealComponent component) {
        return validateAgainstBlacklists(component).isSafe();
    }
}
