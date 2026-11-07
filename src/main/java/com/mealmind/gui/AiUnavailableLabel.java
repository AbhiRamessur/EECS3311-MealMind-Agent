package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.gui.logic.AiAvailability;
import java.util.Objects;
import javafx.scene.control.Label;

/**
 * A visible "AI unavailable: &lt;reason&gt;" label shown next to disabled AI controls. JavaFX shows no tooltips on
 * disabled buttons, so the reason must be visible text. Hidden (and taking no space) when AI is available.
 */
final class AiUnavailableLabel extends Label {

    private final ApplicationContext ctx;

    AiUnavailableLabel(ApplicationContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        getStyleClass().add("hint");
        update();
    }

    /** Re-reads AI availability. */
    void update() {
        String reason = AiAvailability.disabledReason(ctx);
        setText(reason.isEmpty() ? "" : "AI unavailable: " + reason);
        setVisible(!reason.isEmpty());
        setManaged(!reason.isEmpty());
    }
}
