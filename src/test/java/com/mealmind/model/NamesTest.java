package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void normalizeTrimsLowercasesAndCollapsesWhitespace() {
        assertEquals("chicken breast", Names.normalize("  Chicken \t  BREAST "));
        assertEquals("rice", Names.normalize("rice"));
    }

    @Test
    void normalizeRejectsNull() {
        assertThrows(NullPointerException.class, () -> Names.normalize(null));
    }
}
