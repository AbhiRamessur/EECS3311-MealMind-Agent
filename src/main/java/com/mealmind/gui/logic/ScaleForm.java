package com.mealmind.gui.logic;

import com.mealmind.profile.UserProfile;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses the Recipes tab's servings field for scaling. Below 1 uses the domain's message ("Target servings
 * must be at least 1, got N", as in {@code RecipeScaler}); the GUI also caps it at the profile's household
 * maximum of {@value UserProfile#MAX_HOUSEHOLD_SERVINGS}.
 */
public final class ScaleForm {

    /** The largest servings value the GUI accepts. */
    public static final int MAX_SERVINGS = UserProfile.MAX_HOUSEHOLD_SERVINGS;
    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d{1,9}");

    private ScaleForm() {
    }

    /**
     * Parses a servings value.
     *
     * @param text a whole number from 1 to 12
     * @return the number, or one error for the field "servings"
     */
    public static FormResult<Integer> parseServings(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!WHOLE.matcher(trimmed).matches()) {
            return failed(ProfileForm.NOT_A_NUMBER);
        }
        int servings = Integer.parseInt(trimmed);
        if (servings < 1) {
            return failed("Target servings must be at least 1, got " + servings);
        }
        if (servings > MAX_SERVINGS) {
            return failed("Target servings must be at most " + MAX_SERVINGS + ", got " + servings);
        }
        return FormResult.ok(servings);
    }

    private static FormResult<Integer> failed(String message) {
        return FormResult.failed(List.of(new FieldError("servings", message)));
    }
}
