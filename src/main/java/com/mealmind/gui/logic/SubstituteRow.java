package com.mealmind.gui.logic;

/**
 * One suggested substitute in the Recipes tab's "Substitute an ingredient" table.
 *
 * @param numberText    the option number, "1" to "3"
 * @param optionText    "&lt;quantity&gt; &lt;unit&gt; &lt;name&gt; (x&lt;ratio&gt;)"
 * @param noteText      the culinary impact note (may be empty)
 * @param pantryText    "in pantry" when the pantry holds enough, otherwise ""
 * @param nutritionText "about ... kcal, P .. g, C .. g, F .. g (AI estimates)"
 */
public record SubstituteRow(String numberText, String optionText, String noteText, String pantryText, String nutritionText) {
}
