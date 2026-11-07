package com.mealmind.gui.logic;

import java.util.Objects;

/**
 * The status bar's last message and whether it was an error.
 */
public final class StatusMessages {

    /** How a message should look. */
    public enum Level {
        /** Normal feedback. */
        INFO,
        /** Something went wrong. */
        ERROR
    }

    private String message = "";
    private Level level = Level.INFO;

    /**
     * Records an informational message.
     *
     * @param text the message
     */
    public void info(String text) {
        set(text, Level.INFO);
    }

    /**
     * Records an error message.
     *
     * @param text the message
     */
    public void error(String text) {
        set(text, Level.ERROR);
    }

    /**
     * Returns the last message.
     *
     * @return the cleaned message, or empty
     */
    public String message() {
        return message;
    }

    /**
     * Returns the last message's level.
     *
     * @return INFO or ERROR
     */
    public Level level() {
        return level;
    }

    /**
     * Trims a text and collapses runs of whitespace (including line breaks) to single spaces.
     *
     * @param text the text, or null
     * @return the cleaned text ("" for null)
     */
    public static String clean(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    private void set(String text, Level newLevel) {
        message = clean(text);
        level = Objects.requireNonNull(newLevel, "level");
    }
}
