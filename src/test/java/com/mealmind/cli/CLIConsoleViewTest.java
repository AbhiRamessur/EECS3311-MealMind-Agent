package com.mealmind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CLIConsoleViewTest {

    @Test
    void printsOneUpdateLinePerNotification() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        Pantry pantry = new Pantry();
        pantry.addObserver(new CLIConsoleView(out));

        pantry.addItem(new PantryItem("Rice", 1, Unit.KILOGRAM));
        pantry.removeItem("Rice");

        String expected = "[update] Added Rice" + System.lineSeparator()
                + "[update] Removed Rice" + System.lineSeparator();
        assertEquals(expected, buffer.toString(StandardCharsets.UTF_8));
    }
}
