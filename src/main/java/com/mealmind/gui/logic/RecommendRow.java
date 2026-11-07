package com.mealmind.gui.logic;

/**
 * One row of the recommendations table.
 *
 * @param rank   "1" for the best
 * @param id     the recipe id
 * @param name   the recipe name
 * @param reason why it is recommended (deterministic, or the validated AI reason)
 */
public record RecommendRow(String rank, String id, String name, String reason) {
}
