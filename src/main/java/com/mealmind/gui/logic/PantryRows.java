package com.mealmind.gui.logic;

import com.mealmind.cli.CliFormat;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Builds the Pantry tab's rows. Quantities use the CLI's own formatter ({@link CliFormat#amount}, e.g.
 * 1500 g shown as 1.5 kg) and the status words match {@code pantry list}, so both front ends agree.
 */
public final class PantryRows {

    /** Items expiring within this many days are flagged, as in the CLI. */
    public static final int WARNING_DAYS = 3;

    private PantryRows() {
    }

    /**
     * Returns one row per pantry item, in pantry order.
     *
     * @param pantry the pantry
     * @param today  the date expiry is measured from
     * @return the rows
     * @throws NullPointerException if an argument is null
     */
    public static List<PantryRow> of(Pantry pantry, LocalDate today) {
        Objects.requireNonNull(pantry, "pantry");
        Objects.requireNonNull(today, "today");
        return pantry.getItems().stream().map(item -> row(item, today)).toList();
    }

    private static PantryRow row(PantryItem item, LocalDate today) {
        String amount = CliFormat.amount(item.getQuantity(), item.getUnit());
        int space = amount.lastIndexOf(' ');
        String expiry = item.getExpiry().map(LocalDate::toString).orElse("-");
        ExpiryLevel level = ExpiryLevel.NONE;
        String status = "";
        if (item.getExpiry().isPresent()) {
            long days = item.daysUntilExpiry(today);
            if (days < 0) {
                level = ExpiryLevel.EXPIRED;
                status = "EXPIRED";
            } else if (days <= WARNING_DAYS) {
                level = ExpiryLevel.SOON;
                status = days == 0 ? "expires today" : "expires in " + days + (days == 1 ? " day" : " days");
            }
        }
        return new PantryRow(item.getName(), amount.substring(0, space), amount.substring(space + 1), expiry, status, level);
    }
}
