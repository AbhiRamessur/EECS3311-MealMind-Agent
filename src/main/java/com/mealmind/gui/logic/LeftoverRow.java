package com.mealmind.gui.logic;

/**
 * One proposed meal in the Pantry tab's "Use up expiring items" table.
 *
 * @param slotText     e.g. "Day 1 (Monday) BREAKFAST"
 * @param recipeText   the recipe name
 * @param reasonText   why it was chosen (deterministic, or the validated AI reason)
 * @param replacesText "replaces &lt;meal&gt;" when the slot is filled, otherwise ""
 */
public record LeftoverRow(String slotText, String recipeText, String reasonText, String replacesText) {
}
