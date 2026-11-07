package com.mealmind.gui.logic;

import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The form model behind the Pantry tab's add form. Field messages match the {@link PantryItem}
 * constructor's, and every problem is collected. Nothing is added to the pantry here.
 */
public final class PantryForm {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d{1,12}(\\.\\d*)?|\\.\\d+)");

    private PantryForm() {
    }

    /**
     * Parses the add form.
     *
     * @param name     the item name
     * @param quantity a positive decimal number, with "." as the separator
     * @param unit     the unit
     * @param expiry   the expiry date, or null for none
     * @return the item, or the errors for "name", "quantity" and "unit"
     */
    public static FormResult<PantryItem> parse(String name, String quantity, Unit unit, LocalDate expiry) {
        List<FieldError> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add(new FieldError("name", "Pantry item name must not be blank"));
        }
        double amount = 0;
        String text = quantity == null ? "" : quantity.trim();
        if (!DECIMAL.matcher(text).matches()) {
            errors.add(new FieldError("quantity", ProfileForm.NOT_A_NUMBER));
        } else {
            amount = Double.parseDouble(text);
            if (!(amount > 0) || !Double.isFinite(amount)) {
                errors.add(new FieldError("quantity", "Pantry quantity must be a finite number greater than 0, got " + amount));
            }
        }
        if (unit == null) {
            errors.add(new FieldError("unit", "Pantry item unit must not be null"));
        }
        if (!errors.isEmpty()) {
            return FormResult.failed(errors);
        }
        return FormResult.ok(new PantryItem(name, amount, unit, expiry));
    }
}
