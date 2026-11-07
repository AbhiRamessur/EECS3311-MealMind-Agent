package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.cli.CliFormat;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PantryRowsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Test
    void rowsInPantryOrderWithExpiryWordingAndLevels() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Rice", 1500, Unit.GRAM));
        pantry.addItem(new PantryItem("Milk", 1000, Unit.MILLILITER, TODAY.minusDays(1)));
        pantry.addItem(new PantryItem("Eggs", 6, Unit.COUNT, TODAY));
        pantry.addItem(new PantryItem("Spinach", 200, Unit.GRAM, TODAY.plusDays(1)));
        pantry.addItem(new PantryItem("Yogurt", 500, Unit.GRAM, TODAY.plusDays(3)));
        pantry.addItem(new PantryItem("Cheese", 250, Unit.GRAM, TODAY.plusDays(4)));

        List<PantryRow> rows = PantryRows.of(pantry, TODAY);

        // Pantry order is the pantry's own order (sorted by name).
        assertEquals(pantry.getItems().stream().map(PantryItem::getName).toList(), rows.stream().map(PantryRow::name).toList());
        assertEquals(List.of(
                new PantryRow("Cheese", "250", "g", "2026-10-30", "", ExpiryLevel.NONE),
                new PantryRow("Eggs", "6", "pc", "2026-10-26", "expires today", ExpiryLevel.SOON),
                new PantryRow("Milk", "1", "l", "2026-10-25", "EXPIRED", ExpiryLevel.EXPIRED),
                new PantryRow("Rice", "1.5", "kg", "-", "", ExpiryLevel.NONE),
                new PantryRow("Spinach", "200", "g", "2026-10-27", "expires in 1 day", ExpiryLevel.SOON),
                new PantryRow("Yogurt", "500", "g", "2026-10-29", "expires in 3 days", ExpiryLevel.SOON)), rows);
    }

    @Test
    void quantityTextMatchesTheCliWording() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Flour", 2250, Unit.GRAM));
        pantry.addItem(new PantryItem("Oil", 750, Unit.MILLILITER));
        pantry.addItem(new PantryItem("Salt", 0.004, Unit.KILOGRAM));

        for (PantryRow row : PantryRows.of(pantry, TODAY)) {
            PantryItem item = pantry.getItem(row.name()).orElseThrow();
            assertEquals(CliFormat.amount(item.getQuantity(), item.getUnit()), row.quantityText() + " " + row.unitSymbol());
        }
        assertEquals(List.of(), PantryRows.of(new Pantry(), TODAY));
    }
}
