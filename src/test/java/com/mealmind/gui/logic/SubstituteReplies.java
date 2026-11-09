package com.mealmind.gui.logic;

/**
 * Scripted substitution answers for the GUI tests (the agent tests' helper is package-private). Each option's
 * calories match its macros (4*protein + 4*carbs + 9*fat within the plausibility margin).
 */
public final class SubstituteReplies {

    /** Firm tofu, ratio 1: 450 kcal (4*48 + 4*6 + 9*27 = 459). */
    public static final String TOFU = option("Firm tofu", 1.0, "Absorbs the sauce", 450, 48, 6, 27);
    /** Chickpeas, ratio 1.5, no note: 540 kcal (4*30 + 4*90 + 9*9 = 561). */
    public static final String CHICKPEAS = option("Chickpeas", 1.5, "", 540, 30, 90, 9);
    /** Chicken thigh, ratio 1: 600 kcal (4*75 + 9*33 = 597); unsafe for vegetarians. */
    public static final String THIGH = option("Chicken thigh", 1.0, "Juicier", 600, 75, 0, 33);
    /** Peanut butter, ratio 1: 600 kcal (4*25 + 4*20 + 9*50 = 630); unsafe with a peanut allergy. */
    public static final String PEANUT_BUTTER = option("Peanut butter", 1.0, "Rich and nutty", 600, 25, 20, 50);

    private SubstituteReplies() {
    }

    /**
     * One option object.
     *
     * @return the JSON text
     */
    public static String option(String name, double ratio, String note, int kcal, int protein, int carbs, int fat) {
        return "{\"name\": \"" + name + "\", \"ratio\": " + ratio + ", \"note\": \"" + note + "\", \"calories\": " + kcal
                + ", \"proteinG\": " + protein + ", \"carbsG\": " + carbs + ", \"fatG\": " + fat + ", \"cost\": 2}";
    }

    /**
     * A complete final answer.
     *
     * @param options option objects
     * @return the JSON text
     */
    public static String reply(String... options) {
        return "{\"substitutions\": [" + String.join(", ", options) + "]}";
    }
}
