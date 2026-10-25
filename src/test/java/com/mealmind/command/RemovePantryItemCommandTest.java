package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class RemovePantryItemCommandTest {

    @Test
    void executeRemovesAndUndoRestoresExactly() {
        Pantry pantry = new Pantry();
        PantryItem milk = new PantryItem("Milk", 1, Unit.LITER, LocalDate.of(2026, 10, 12));
        pantry.addItem(milk);
        RemovePantryItemCommand command = new RemovePantryItemCommand(pantry, " MILK ");

        command.execute();
        assertTrue(pantry.isEmpty());

        command.undo();
        assertEquals(List.of(milk), pantry.getItems());
        assertEquals("Remove MILK from pantry", command.description());
    }

    @Test
    void missingItemThrowsAndChangesNothing() {
        Pantry pantry = new Pantry();
        PantryItem rice = new PantryItem("Rice", 1, Unit.KILOGRAM);
        pantry.addItem(rice);
        RemovePantryItemCommand command = new RemovePantryItemCommand(pantry, "Tofu");

        assertThrows(NoSuchElementException.class, command::execute);

        assertEquals(List.of(rice), pantry.getItems());
        assertThrows(IllegalStateException.class, command::undo);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new RemovePantryItemCommand(null, "Rice"));
        assertThrows(NullPointerException.class, () -> new RemovePantryItemCommand(new Pantry(), null));
    }
}
