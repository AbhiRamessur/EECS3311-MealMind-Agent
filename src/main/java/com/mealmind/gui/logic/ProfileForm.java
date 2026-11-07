package com.mealmind.gui.logic;

import com.mealmind.model.Names;
import com.mealmind.profile.DietType;
import com.mealmind.profile.ProfileData;
import com.mealmind.profile.UserProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The form model behind the Profile tab: turns the typed texts into a {@link ProfileData} or a list of
 * field errors. It never applies anything. Ranges are checked by {@link UserProfile} itself (on a scratch
 * profile), so the GUI shows exactly the CLI's messages and the two cannot drift.
 */
public final class ProfileForm {

    /** Shown when a number field holds text that is not a number. */
    public static final String NOT_A_NUMBER = "must be a number";
    /** Shown when household servings is empty. */
    public static final String REQUIRED = "is required";

    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d{1,9}");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d{1,12}(\\.\\d*)?|\\.\\d+)");
    private static final String[] FIELDS = {"diet", "calories", "protein", "carbs", "fat", "budget", "servings"};

    private ProfileForm() {
    }

    /**
     * Returns the field names used in {@link FieldError}s, in form order.
     *
     * @return a new array: diet, calories, protein, carbs, fat, budget, servings
     */
    public static String[] fieldNames() {
        return FIELDS.clone();
    }

    /**
     * Parses the form. Blank or "none" (any case) means "not set" for the targets and budget; servings is
     * required. Every problem is collected, in form order.
     *
     * @param diet      the diet name
     * @param allergens the allergen names
     * @param calories  whole kcal, blank or "none"
     * @param protein   grams, blank or "none"
     * @param carbs     grams, blank or "none"
     * @param fat       grams, blank or "none"
     * @param budget    dollars, blank or "none"
     * @param servings  whole number of people
     * @return the profile values, or the errors
     */
    public static FormResult<ProfileData> parse(String diet, Set<String> allergens, String calories, String protein,
                                                String carbs, String fat, String budget, String servings) {
        List<FieldError> errors = new ArrayList<>();
        DietType dietType = DietType.OMNIVORE;
        try {
            dietType = DietType.parse(diet);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError("diet", e.getMessage()));
        }
        Integer kcal = number("calories", calories, WHOLE, Integer::valueOf, errors);
        Double proteinG = number("protein", protein, DECIMAL, Double::valueOf, errors);
        Double carbsG = number("carbs", carbs, DECIMAL, Double::valueOf, errors);
        Double fatG = number("fat", fat, DECIMAL, Double::valueOf, errors);
        Double weekly = number("budget", budget, DECIMAL, Double::valueOf, errors);
        Integer people = null;
        if (isUnset(servings)) {
            errors.add(new FieldError("servings", REQUIRED));
        } else {
            people = number("servings", servings, WHOLE, Integer::valueOf, errors);
        }
        Set<String> names = allergens == null ? Set.of() : allergens;

        // Each value is checked alone on a scratch profile, so every range problem is reported for its own field.
        ProfileData d = ProfileData.defaults();
        check("calories", new ProfileData(d.diet(), Set.of(), kcal, null, null, null, null, 1), errors);
        check("protein", new ProfileData(d.diet(), Set.of(), null, proteinG, null, null, null, 1), errors);
        check("carbs", new ProfileData(d.diet(), Set.of(), null, null, carbsG, null, null, 1), errors);
        check("fat", new ProfileData(d.diet(), Set.of(), null, null, null, fatG, null, 1), errors);
        check("budget", new ProfileData(d.diet(), Set.of(), null, null, null, null, weekly, 1), errors);
        if (people != null) {
            check("servings", new ProfileData(d.diet(), Set.of(), null, null, null, null, null, people), errors);
        }
        check("form", new ProfileData(d.diet(), names, null, null, null, null, null, 1), errors);
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(ProfileForm::order));
            return FormResult.failed(errors);
        }
        // Stored the way the profile normalizes them, so the form shows exactly what will be saved.
        Set<String> normalized = new TreeSet<>();
        names.forEach(name -> allergenName(name).ifPresent(normalized::add));
        ProfileData data = new ProfileData(dietType, normalized, kcal, proteinG, carbsG, fatG, weekly, people);
        check("form", data, errors);
        return errors.isEmpty() ? FormResult.ok(data) : FormResult.failed(errors);
    }

    /**
     * Returns the texts to show for a profile.
     *
     * @param data the profile values
     * @return the form texts (blank for unset values)
     * @throws NullPointerException if {@code data} is null
     */
    public static ProfileFormValues toForm(ProfileData data) {
        Objects.requireNonNull(data, "data");
        return new ProfileFormValues(data.diet().getDisplayName(), List.copyOf(data.allergens()),
                text(data.calorieTarget()), text(data.proteinTargetG()), text(data.carbsTargetG()),
                text(data.fatTargetG()), text(data.weeklyBudget()), String.valueOf(data.householdServings()));
    }

    /**
     * Normalizes an allergen name the way the profile stores it.
     *
     * @param text the typed name
     * @return the normalized name, or empty if blank
     */
    public static Optional<String> allergenName(String text) {
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(Names.normalize(text));
    }

    private static <N> N number(String field, String text, Pattern pattern, Function<String, N> parser,
                                List<FieldError> errors) {
        if (isUnset(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (!pattern.matcher(trimmed).matches()) {
            errors.add(new FieldError(field, NOT_A_NUMBER));
            return null;
        }
        return parser.apply(trimmed);
    }

    /** Form order of an error's field; "form" errors come last. */
    private static int order(FieldError error) {
        int index = Arrays.asList(FIELDS).indexOf(error.field());
        return index < 0 ? FIELDS.length : index;
    }

    private static boolean isUnset(String text) {
        return text == null || text.isBlank() || text.trim().toLowerCase(Locale.ROOT).equals("none");
    }

    /** Runs {@code data} through a scratch profile and records its message for {@code field}. */
    private static void check(String field, ProfileData data, List<FieldError> errors) {
        try {
            new UserProfile().applyData(data);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError(field, e.getMessage()));
        }
    }

    private static String text(Number value) {
        if (value == null) {
            return "";
        }
        return value instanceof Double d ? BigDecimal.valueOf(d).stripTrailingZeros().toPlainString() : value.toString();
    }
}
