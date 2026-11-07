package com.mealmind.gui.logic;

import com.mealmind.recommend.RecommendationRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses the Recipes tab's recommendation fields into a {@link RecommendationRequest}. Range messages come
 * from the request's own validation, so the GUI and the domain agree; every error is collected.
 */
public final class RecommendForm {

    /** The limit field's initial text. */
    public static final String DEFAULT_LIMIT = String.valueOf(RecommendationRequest.defaults().limit());
    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d{1,9}");

    private RecommendForm() {
    }

    /**
     * Parses the form.
     *
     * @param pantryFirst rank by pantry coverage first
     * @param maxMinutes  a whole number of minutes from 1, or blank for no limit
     * @param limit       a whole number from 1 to 10
     * @return the request, or errors for "maxMinutes" and "limit"
     */
    public static FormResult<RecommendationRequest> parse(boolean pantryFirst, String maxMinutes, String limit) {
        List<FieldError> errors = new ArrayList<>();
        Integer minutes = null;
        if (maxMinutes != null && !maxMinutes.isBlank()) {
            minutes = whole("maxMinutes", maxMinutes, errors);
            if (minutes != null) {
                check("maxMinutes", minutes, RecommendationRequest.defaults().limit(), errors);
            }
        }
        Integer count = whole("limit", limit, errors);
        if (count != null) {
            check("limit", null, count, errors);
        }
        if (!errors.isEmpty()) {
            return FormResult.failed(errors);
        }
        return FormResult.ok(new RecommendationRequest(pantryFirst, minutes, count));
    }

    private static Integer whole(String field, String text, List<FieldError> errors) {
        String trimmed = text == null ? "" : text.trim();
        if (!WHOLE.matcher(trimmed).matches()) {
            errors.add(new FieldError(field, ProfileForm.NOT_A_NUMBER));
            return null;
        }
        return Integer.valueOf(trimmed);
    }

    /** Builds a request with one value under test to obtain the domain's message for it. */
    private static void check(String field, Integer minutes, int count, List<FieldError> errors) {
        try {
            new RecommendationRequest(false, minutes, count);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError(field, e.getMessage()));
        }
    }
}
