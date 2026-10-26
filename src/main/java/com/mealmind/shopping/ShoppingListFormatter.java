package com.mealmind.shopping;

import com.mealmind.model.RecipeScaler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a {@link ShoppingList} as plain text for the CLI and for text export.
 *
 * <p>Quantities are rounded with {@link RecipeScaler#roundForDisplay} for readability. That rounding
 * is presentation only and never changes the list's stored values.
 */
public final class ShoppingListFormatter {

    private ShoppingListFormatter() {
    }

    /**
     * Formats the list. Layout: a title line; each warning on a line starting {@code "! "}; for each
     * non-empty aisle a blank line, the aisle name and one line per item
     * ({@code "- 500 g Chicken Breast (~$7.50) [note]"}); then a blank line and the estimated total.
     * An empty list prints {@code "Nothing to buy."} instead of the aisle sections. Every line ends
     * with {@code "\n"} and numbers use {@link Locale#ROOT}.
     *
     * @param list the list to format
     * @return the text
     * @throws NullPointerException if {@code list} is null
     */
    public static String toText(ShoppingList list) {
        Objects.requireNonNull(list, "list");
        StringBuilder text = new StringBuilder("MealMind Shopping List\n");
        for (String warning : list.getWarnings()) {
            text.append("! ").append(warning).append('\n');
        }
        if (list.isEmpty()) {
            text.append("Nothing to buy.\n");
        }
        for (Map.Entry<Aisle, List<ShoppingItem>> section : list.byAisle().entrySet()) {
            text.append('\n').append(section.getKey().getDisplayName()).append(":\n");
            for (ShoppingItem item : section.getValue()) {
                text.append("- ").append(quantity(item)).append(' ').append(item.getUnit().getSymbol())
                        .append(' ').append(item.getName())
                        .append(" (~$").append(money(item.getEstimatedCost())).append(')');
                item.getNote().ifPresent(note -> text.append(" [").append(note).append(']'));
                text.append('\n');
            }
        }
        text.append("\nEstimated total: $").append(money(list.getTotalEstimatedCost())).append('\n');
        return text.toString();
    }

    /** Display quantity without a trailing ".0"; a positive amount that rounds to 0 keeps up to 2 decimals. */
    private static String quantity(ShoppingItem item) {
        double rounded = RecipeScaler.roundForDisplay(item.getQuantity(), item.getUnit());
        BigDecimal shown = rounded > 0
                ? BigDecimal.valueOf(rounded)
                : BigDecimal.valueOf(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        if (shown.signum() == 0) {
            // Smaller than 0.005: show it unrounded rather than as 0.
            shown = BigDecimal.valueOf(item.getQuantity());
        }
        return shown.stripTrailingZeros().toPlainString();
    }

    private static String money(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
