package com.mealmind.gui.logic;

import java.util.List;

/**
 * The Generate section's preview of the pending AI recipe.
 *
 * @param present      whether there is a pending recipe
 * @param title        e.g. "Generated recipe AI001: Chickpea Pasta", or "No pending recipe."
 * @param lines        the preview lines, as the CLI prints them (unmodifiable)
 * @param safe         whether it passed the safety check (false when absent)
 * @param safetyBanner "Safety check: passed", the CLI's SAFETY WARNING block, or empty when absent
 */
public record PendingView(boolean present, String title, List<String> lines, boolean safe, String safetyBanner) {

    /** Copies the lines. */
    public PendingView {
        lines = List.copyOf(lines);
    }
}
