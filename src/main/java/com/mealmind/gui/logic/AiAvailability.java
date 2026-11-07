package com.mealmind.gui.logic;

import com.mealmind.app.ApplicationContext;
import java.util.Objects;

/**
 * Tells the GUI whether to disable its AI buttons, and why.
 */
public final class AiAvailability {

    private AiAvailability() {
    }

    /**
     * Returns why AI is disabled.
     *
     * @param ctx the session
     * @return the reason (as {@code LlmService} reports it), or "" when AI is available
     * @throws NullPointerException if {@code ctx} is null
     */
    public static String disabledReason(ApplicationContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        return ctx.llm().isAvailable() ? "" : ctx.llm().unavailableReason().orElse("not configured");
    }

    /**
     * Returns the tooltip for a disabled AI control.
     *
     * @param reason the reason from {@link #disabledReason}
     * @return "AI features are disabled: &lt;reason&gt;"
     */
    public static String tooltip(String reason) {
        return "AI features are disabled: " + reason;
    }
}
