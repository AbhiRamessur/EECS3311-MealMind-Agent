package com.mealmind.cli;

import com.mealmind.model.MealComponent;
import com.mealmind.model.RecipeScaler;
import com.mealmind.model.Unit;
import com.mealmind.model.UnitConverter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Number formatting for CLI output. Display only: stored values are never changed.
 */
final class CliFormat {

    private CliFormat() {
    }

    /**
     * Formats a quantity with its unit, e.g. {@code "1.5 kg"}. Gram and milliliter amounts of 1000 or
     * more are shown in kg or l, and the number is rounded with {@link RecipeScaler#roundForDisplay}.
     */
    static String amount(double quantity, Unit unit) {
        Unit shown = unit;
        if (unit == Unit.GRAM && quantity >= 1000) {
            shown = Unit.KILOGRAM;
        } else if (unit == Unit.MILLILITER && quantity >= 1000) {
            shown = Unit.LITER;
        }
        double value = UnitConverter.convert(quantity, unit, shown);
        return number(value, shown) + " " + shown.getSymbol();
    }

    /** A display number without a trailing ".0"; a positive amount that would round to 0 keeps 2 decimals. */
    static String number(double quantity, Unit unit) {
        double rounded = RecipeScaler.roundForDisplay(quantity, unit);
        BigDecimal shown = rounded > 0 || quantity == 0
                ? BigDecimal.valueOf(rounded)
                : BigDecimal.valueOf(quantity).setScale(2, RoundingMode.HALF_UP);
        return shown.stripTrailingZeros().toPlainString();
    }

    /** Calories, macros and cost of a component divided by {@code servings}, on one line. */
    static String nutrition(MealComponent component, int servings) {
        return String.format(Locale.ROOT, "%.0f kcal, %.1f g protein, %.1f g carbs, %.1f g fat, $%.2f",
                component.getCalories() / servings, component.getProteinG() / servings,
                component.getCarbsG() / servings, component.getFatG() / servings, component.getCost() / servings);
    }
}
