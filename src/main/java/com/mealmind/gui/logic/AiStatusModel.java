package com.mealmind.gui.logic;

import com.mealmind.llm.LlmService;
import java.util.Objects;

/**
 * The toolbar's AI status text. It shows only the provider, the model and whether AI is available; no
 * other configuration value (and never a key) is ever displayed.
 */
public final class AiStatusModel {

    /** Longest reason shown before it is cut. */
    public static final int MAX_REASON = 80;

    private final LlmService llm;

    /**
     * Creates the model.
     *
     * @param llm the session's AI service
     * @throws NullPointerException if {@code llm} is null
     */
    public AiStatusModel(LlmService llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    /**
     * Returns whether AI is available.
     *
     * @return {@code true} if a provider is configured
     */
    public boolean available() {
        return llm.isAvailable();
    }

    /**
     * Returns the status text.
     *
     * @return "AI: &lt;provider&gt; (&lt;model&gt;)" or "AI disabled: &lt;reason&gt;" (cut to 80 characters plus "...")
     */
    public String text() {
        if (llm.isAvailable()) {
            return "AI: " + llm.providerName().orElse("unknown") + " (" + llm.modelName().orElse("unknown") + ")";
        }
        String reason = StatusMessages.clean(llm.unavailableReason().orElse("not configured"));
        return "AI disabled: " + (reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON) + "...");
    }
}
