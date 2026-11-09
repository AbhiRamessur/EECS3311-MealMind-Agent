package com.mealmind.gui.logic;

import com.mealmind.leftovers.LeftoverRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Parses the "Use up expiring items" form on the Pantry tab into a {@link LeftoverRequest}. The bounds and
 * messages are {@link LeftoverRequest}'s own; every problem is collected (fields "days" and "meals").
 */
public final class LeftoverForm {

    /** Any valid value, used to check one field at a time. */
    private static final int VALID = 1;

    private LeftoverForm() {
    }

    /**
     * Parses the form.
     *
     * @param days      the "Days" text (whole number of days to look ahead)
     * @param meals     the "Meals" text (whole number of meals to propose)
     * @param overwrite whether filled slots may be replaced
     * @return the request, or every field error
     */
    public static FormResult<LeftoverRequest> parse(String days, String meals, boolean overwrite) {
        List<FieldError> errors = new ArrayList<>();
        OptionalInt dayCount = whole(days, "days", errors);
        if (dayCount.isPresent()) {
            check(() -> new LeftoverRequest(dayCount.getAsInt(), VALID, overwrite), "days", errors);
        }
        OptionalInt mealCount = whole(meals, "meals", errors);
        if (mealCount.isPresent()) {
            check(() -> new LeftoverRequest(VALID, mealCount.getAsInt(), overwrite), "meals", errors);
        }
        if (!errors.isEmpty()) {
            return FormResult.failed(errors);
        }
        return FormResult.ok(new LeftoverRequest(dayCount.getAsInt(), mealCount.getAsInt(), overwrite));
    }

    private static OptionalInt whole(String text, String field, List<FieldError> errors) {
        try {
            return OptionalInt.of(Integer.parseInt(text == null ? "" : text.trim()));
        } catch (NumberFormatException e) {
            errors.add(new FieldError(field, ProfileForm.NOT_A_NUMBER));
            return OptionalInt.empty();
        }
    }

    private static void check(Runnable construct, String field, List<FieldError> errors) {
        try {
            construct.run();
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError(field, e.getMessage()));
        }
    }
}
