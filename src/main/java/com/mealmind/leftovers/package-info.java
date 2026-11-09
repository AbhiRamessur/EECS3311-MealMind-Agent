/**
 * Expiring-leftover planning (F10): finds pantry items that expire soon, chooses safe recipes from the
 * recipe book that use them ({@link com.mealmind.planning.LeftoverStrategy}), proposes them for plan
 * slots and applies an accepted proposal as one undoable {@link com.mealmind.command.MacroCommand}.
 * The result is deterministic; AI may only reorder the safe candidates.
 */
package com.mealmind.leftovers;
