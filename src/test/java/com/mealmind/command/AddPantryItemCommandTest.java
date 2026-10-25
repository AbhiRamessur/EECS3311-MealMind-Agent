package com.mealmind.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AddPantryItemCommandTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 10, 20);

    @Test
    void executeAddsAndUndoRemovesNewItem() {
        Pantry pantry = new Pantry();
        AddPantryItemCommand command = new AddPantryItemCommand(pantry, new PantryItem("Rice", 1, Unit.KILOGRAM));

        command.execute();
        assertEquals(1, pantry.size());

        command.undo();
        assertTrue(pantry.isEmpty());
        assertEquals("Add Rice to pantry", command.description());
    }

    @Test
    void undoOfMergeRestoresExactEarlierItemAndRedoReMerges() {
        Pantry pantry = new Pantry();
        PantryItem before = new PantryItem("Chicken Breast", 500, Unit.GRAM, EXPIRY);
        pantry.addItem(before);
        AddPantryItemCommand command = new AddPantryItemCommand(pantry,
                new PantryItem("chicken breast", 1, Unit.KILOGRAM, EXPIRY.plusDays(5)));

        command.execute();
        assertEquals(1500, pantry.getItem("chicken breast").orElseThrow().getQuantity(), 1e-9);

        command.undo();
        assertEquals(List.of(before), pantry.getItems());

        command.execute();
        assertEquals(1500, pantry.getItem("chicken breast").orElseThrow().getQuantity(), 1e-9);
    }

    @Test
    void executeTwiceOrUndoFirstThrows() {
        Pantry pantry = new Pantry();
        AddPantryItemCommand command = new AddPantryItemCommand(pantry, new PantryItem("Rice", 1, Unit.KILOGRAM));

        assertThrows(IllegalStateException.class, command::undo);
        command.execute();
        assertThrows(IllegalStateException.class, command::execute);
    }

    @Test
    void incompatibleUnitsPropagateAndLeaveEverythingUntouched() {
        Pantry pantry = new Pantry();
        PantryItem flour = new PantryItem("Flour", 500, Unit.GRAM);
        pantry.addItem(flour);
        AddPantryItemCommand command = new AddPantryItemCommand(pantry, new PantryItem("Flour", 1, Unit.LITER));

        assertThrows(IncompatibleUnitException.class, command::execute);

        assertEquals(List.of(flour), pantry.getItems());
        assertThrows(IllegalStateException.class, command::undo);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new AddPantryItemCommand(null, new PantryItem("Rice", 1, Unit.GRAM)));
        assertThrows(NullPointerException.class, () -> new AddPantryItemCommand(new Pantry(), null));
    }
}
