package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StatusMessagesTest {

    @Test
    void cleanTrimsAndCollapsesWhitespace() {
        assertEquals("Added Rice.", StatusMessages.clean("  Added \n\t Rice.  "));
        assertEquals("", StatusMessages.clean(null));
    }

    @Test
    void tracksTheLastMessageAndLevel() {
        StatusMessages messages = new StatusMessages();
        assertEquals("", messages.message());
        assertEquals(StatusMessages.Level.INFO, messages.level());

        messages.error(" Item not added:\nbad unit ");
        assertEquals("Item not added: bad unit", messages.message());
        assertEquals(StatusMessages.Level.ERROR, messages.level());

        messages.info("Profile saved.");
        assertEquals("Profile saved.", messages.message());
        assertEquals(StatusMessages.Level.INFO, messages.level());
    }
}
