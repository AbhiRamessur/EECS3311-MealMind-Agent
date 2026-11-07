package com.mealmind.gui.logic;

/**
 * One entry of the planning strategy selector.
 *
 * @param id          the strategy id, e.g. "pantry-first"
 * @param displayName the shown name, e.g. "Pantry-First"
 */
public record StrategyOption(String id, String displayName) {
}
