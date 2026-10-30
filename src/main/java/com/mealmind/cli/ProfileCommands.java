package com.mealmind.cli;

import com.mealmind.model.Names;
import com.mealmind.profile.DietType;
import com.mealmind.profile.ProfileData;
import com.mealmind.profile.UserProfile;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code profile} commands (F01, deterministic part). Profile edits are settings, not plan state,
 * so they change the {@link UserProfile} directly and are not added to the undo history.
 */
final class ProfileCommands {

    static final String USAGE = "profile show | profile diet <type> | profile allergy add|remove \"<name>\""
            + " | profile update [--calories n] [--protein g] [--carbs g] [--fat g] [--budget n] [--servings n]";
    static final String DIET_USAGE = "profile diet <omnivore|vegetarian|vegan|pescatarian>";
    static final String ALLERGY_USAGE = "profile allergy add|remove \"<name>\"";
    static final String UPDATE_USAGE = "profile update [--calories n] [--protein g] [--carbs g] [--fat g]"
            + " [--budget n] [--servings n]   (use 'none' to clear a target or the budget)";

    private static final Set<String> UPDATE_FLAGS = Set.of("calories", "protein", "carbs", "fat", "budget", "servings");

    private final UserProfile profile;
    private final PrintStream out;

    ProfileCommands(UserProfile profile, PrintStream out) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** Runs {@code profile <subcommand> ...}. */
    void handle(List<String> args) {
        if (args.isEmpty()) {
            throw new UsageException(USAGE);
        }
        List<String> rest = args.subList(1, args.size());
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "show" -> show();
            case "diet" -> diet(rest);
            case "allergy" -> allergy(rest);
            case "update" -> update(rest);
            default -> throw new UsageException(USAGE);
        }
    }

    private void show() {
        Set<String> allergens = profile.getAllergens();
        out.println("Diet: " + profile.getDiet().getDisplayName());
        out.println("Allergens: " + (allergens.isEmpty() ? "none" : String.join(", ", allergens)));
        out.println("Calorie target: " + profile.getCalorieTarget().map(c -> c + " kcal/day").orElse("not set"));
        out.println("Protein target: " + grams(profile.getProteinTargetG()));
        out.println("Carbs target: " + grams(profile.getCarbsTargetG()));
        out.println("Fat target: " + grams(profile.getFatTargetG()));
        out.println("Weekly budget: " + profile.getWeeklyBudget()
                .map(b -> String.format(Locale.ROOT, "$%.2f", b)).orElse("not set"));
        out.println("Household servings: " + profile.getHouseholdServings());
    }

    private static String grams(Optional<Double> value) {
        return value.map(g -> number(g) + " g/day").orElse("not set");
    }

    private void diet(List<String> args) {
        if (args.size() != 1) {
            throw new UsageException(DIET_USAGE);
        }
        DietType diet = DietType.parse(args.get(0));
        profile.setDiet(diet);
        out.println("Diet set to " + diet.getDisplayName() + ".");
    }

    private void allergy(List<String> args) {
        if (args.size() != 2) {
            throw new UsageException(ALLERGY_USAGE);
        }
        String name = args.get(1);
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "add" -> out.println(profile.addAllergen(name)
                    ? "Allergen added: " + normalized(name) : "Allergen already listed: " + normalized(name));
            case "remove" -> out.println(profile.removeAllergen(name)
                    ? "Allergen removed: " + normalized(name) : "Allergen not listed: " + normalized(name));
            default -> throw new UsageException(ALLERGY_USAGE);
        }
    }

    private static String normalized(String name) {
        return Names.normalize(name);
    }

    /** Builds the complete new profile first, then applies it in one validated step. */
    private void update(List<String> args) {
        CliArgs parsed = CliArgs.parse(args, UPDATE_FLAGS, UPDATE_USAGE).expectPositional(0, 0, UPDATE_USAGE);
        if (parsed.flags().isEmpty()) {
            throw new UsageException(UPDATE_USAGE);
        }
        ProfileData old = profile.toData();
        ProfileData updated = new ProfileData(old.diet(), old.allergens(),
                parsed.flags().containsKey("calories") ? intOrNone(parsed.flags().get("calories")) : old.calorieTarget(),
                parsed.flags().containsKey("protein") ? doubleOrNone(parsed.flags().get("protein")) : old.proteinTargetG(),
                parsed.flags().containsKey("carbs") ? doubleOrNone(parsed.flags().get("carbs")) : old.carbsTargetG(),
                parsed.flags().containsKey("fat") ? doubleOrNone(parsed.flags().get("fat")) : old.fatTargetG(),
                parsed.flags().containsKey("budget") ? doubleOrNone(parsed.flags().get("budget")) : old.weeklyBudget(),
                parsed.flags().containsKey("servings")
                        ? CliArgs.parseInt(parsed.flags().get("servings"), UPDATE_USAGE) : old.householdServings());
        profile.applyData(updated);

        List<String> changes = new ArrayList<>();
        addChange(changes, "calories", old.calorieTarget(), updated.calorieTarget());
        addChange(changes, "protein", old.proteinTargetG(), updated.proteinTargetG());
        addChange(changes, "carbs", old.carbsTargetG(), updated.carbsTargetG());
        addChange(changes, "fat", old.fatTargetG(), updated.fatTargetG());
        addChange(changes, "budget", old.weeklyBudget(), updated.weeklyBudget());
        addChange(changes, "servings", old.householdServings(), updated.householdServings());
        out.println(changes.isEmpty() ? "Profile unchanged." : "Profile updated: " + String.join(", ", changes));
    }

    private static void addChange(List<String> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(field + " " + (after == null ? "cleared"
                    : after instanceof Double d ? number(d) : after.toString()));
        }
    }

    private static Integer intOrNone(String text) {
        return text.equalsIgnoreCase("none") ? null : CliArgs.parseInt(text, UPDATE_USAGE);
    }

    private static Double doubleOrNone(String text) {
        return text.equalsIgnoreCase("none") ? null : CliArgs.parseDouble(text, UPDATE_USAGE);
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
