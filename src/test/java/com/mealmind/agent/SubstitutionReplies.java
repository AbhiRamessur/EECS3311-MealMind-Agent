package com.mealmind.agent;

import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.model.Unit;
import java.util.List;

/**
 * Scripted substitution replies and a fixture recipe. Every option's calories match its macros
 * (4*protein + 4*carbs + 9*fat), so it passes the plausibility check.
 */
final class SubstitutionReplies {

    static final Ingredient NOODLES = new Ingredient("Noodles", 200, Unit.GRAM, 700, 24, 140, 4, 1.00);
    static final Ingredient PEANUT_BUTTER = new Ingredient("Peanut butter", 100, Unit.GRAM, 600, 24, 20, 50, 2.00);
    static final Ingredient SOY_SAUCE = new Ingredient("Soy sauce", 20, Unit.MILLILITER, 10, 2, 1, 0, 0.20);

    /** "Peanut Noodles" [T1], 2 servings; the peanut butter (index 1) is the usual target. */
    static final Recipe RECIPE = new Recipe("T1", "Peanut Noodles", 2, 15,
            List.of("Cook the noodles.", "Stir in the peanut butter and soy sauce."), List.of(NOODLES, PEANUT_BUTTER, SOY_SAUCE));

    private SubstitutionReplies() {
    }

    /** One option: 100 kcal from 5 g protein, 10 g carbs and 4 g fat (20 + 40 + 36 = 96). */
    static String option(String name, double ratio) {
        return "{\"name\": \"" + name + "\", \"ratio\": " + ratio + ", \"note\": \"Works well\", \"calories\": 100, "
                + "\"proteinG\": 5, \"carbsG\": 10, \"fatG\": 4, \"cost\": 1.5}";
    }

    /** The value of the "substitutions" key. */
    static String array(String... options) {
        return "[" + String.join(", ", options) + "]";
    }

    /** A complete final-answer reply. */
    static String reply(String... options) {
        return "{\"substitutions\": " + array(options) + "}";
    }
}
