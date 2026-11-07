package com.mealmind.agent;

import java.text.Normalizer;

/**
 * Cleans free text from a model reply before it is checked and shown: control characters and line
 * breaks become single spaces, common typographic punctuation becomes its ASCII form, accents are
 * removed, and any other non-ASCII character is dropped, so console output stays ASCII.
 */
final class ReplyText {

    private ReplyText() {
    }

    /** Returns the cleaned, trimmed text. */
    static String clean(String text) {
        String ascii = text.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replace("…", "...");
        ascii = Normalizer.normalize(ascii, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.replaceAll("[^\\x20-\\x7E]", " ").replaceAll("\\s+", " ").trim();
    }
}
