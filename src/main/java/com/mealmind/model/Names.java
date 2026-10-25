package com.mealmind.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Canonical form for ingredient names, so that names typed differently still match.
 */
public final class Names {

    private Names() {
    }

    /**
     * Normalizes a name: trimmed, lowercase, with runs of whitespace collapsed to one space.
     * For example {@code "  Chicken   BREAST "} becomes {@code "chicken breast"}.
     *
     * @param name the name to normalize
     * @return the normalized name
     * @throws NullPointerException if {@code name} is null
     */
    public static String normalize(String name) {
        return Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
