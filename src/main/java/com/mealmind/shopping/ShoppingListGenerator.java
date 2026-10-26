package com.mealmind.shopping;

import com.mealmind.model.ChangeEvent;
import com.mealmind.model.Dimension;
import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Ingredient;
import com.mealmind.model.MealPlan;
import com.mealmind.model.ModelObserver;
import com.mealmind.model.ObservableSubject;
import com.mealmind.model.Unit;
import com.mealmind.model.UnitConverter;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the shopping list (F11 / UC11 / SD11): what the meal plan needs minus what the pantry has.
 *
 * <p><strong>Observer pattern, two roles.</strong> It is the system's first ConcreteObserver: it
 * registers on a {@link MealPlan} and a {@link Pantry} (the ConcreteSubjects) and recomputes its
 * cached list in {@link #onStateChanged} whenever either one changes. It is also a ConcreteSubject
 * itself: after each recompute it notifies its own observers once, so views can subscribe to the
 * generator and always show an up-to-date list without knowing about the plan or the pantry. Stage 1
 * named the callback {@code update()}; it is {@code onStateChanged()} to match {@link ModelObserver}.
 *
 * <p>The algorithm is in {@link #compareAgainstPantry}, a pure static method named after the Stage 1
 * traceability matrix. Limitation: expired pantry stock still counts as available; expiry-aware
 * shopping belongs to a later feature.
 */
public final class ShoppingListGenerator extends ObservableSubject implements ModelObserver {

    /** Remaining amounts at or below this are treated as fully covered (floating-point tolerance). */
    private static final double EPSILON = 1e-9;

    private static final String SEVERAL_UNITS_NOTE = "Needed in several incompatible units; listed separately";

    private final MealPlan plan;
    private final Pantry pantry;
    private ShoppingList current;

    /**
     * Creates a generator, subscribes it to both subjects and computes the initial list (without
     * notifying anyone).
     *
     * @param plan   the meal plan whose ingredients are required
     * @param pantry the pantry whose stock is subtracted
     * @throws NullPointerException if an argument is null
     */
    public ShoppingListGenerator(MealPlan plan, Pantry pantry) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        plan.addObserver(this);
        pantry.addObserver(this);
        this.current = generate();
    }

    /** The total need for one ingredient name in one dimension, in its first-seen unit. */
    private static final class Requirement {
        final String name;
        final Unit unit;
        double quantity;
        double cost;

        Requirement(Ingredient first) {
            this.name = first.getName();
            this.unit = first.getUnit();
        }

        void add(Ingredient ingredient) {
            quantity += UnitConverter.convert(ingredient.getQuantity(), ingredient.getUnit(), unit);
            cost += ingredient.getCost();
        }
    }

    /**
     * Computes what still has to be bought.
     *
     * <ol>
     *   <li>Required ingredients with quantity 0 are ignored. The rest are grouped by normalized name
     *       and, within a name, by unit {@link Dimension}; each group's quantities are converted into
     *       its first-seen unit and summed, and their cost totals are summed.</li>
     *   <li>The pantry's stock is subtracted. If the pantry holds the item in an incompatible unit,
     *       nothing is subtracted and a note explains why.</li>
     *   <li>Fully covered groups are left out. Otherwise the item's cost is the group's cost scaled by
     *       the fraction still missing.</li>
     *   <li>A name needed in more than one dimension produces one item per dimension, each noted.</li>
     * </ol>
     *
     * @param required the ingredients the plan needs, unconsolidated
     * @param pantry   the current pantry
     * @return the shopping list, with warnings for an empty plan or an empty pantry
     * @throws NullPointerException if an argument is null
     */
    public static ShoppingList compareAgainstPantry(List<Ingredient> required, Pantry pantry) {
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(pantry, "pantry");

        Map<String, Map<Dimension, Requirement>> byName = new LinkedHashMap<>();
        for (Ingredient ingredient : required) {
            if (ingredient.getQuantity() == 0) {
                continue;
            }
            byName.computeIfAbsent(ingredient.normalizedName(), name -> new LinkedHashMap<>())
                    .computeIfAbsent(ingredient.getUnit().getDimension(), dimension -> new Requirement(ingredient))
                    .add(ingredient);
        }

        List<ShoppingItem> items = new ArrayList<>();
        for (Map<Dimension, Requirement> dimensions : byName.values()) {
            for (Requirement requirement : dimensions.values()) {
                List<String> notes = new ArrayList<>();
                double available = available(requirement, pantry, notes);
                double missing = requirement.quantity - available;
                if (missing <= EPSILON) {
                    continue;
                }
                if (dimensions.size() > 1) {
                    notes.add(SEVERAL_UNITS_NOTE);
                }
                double cost = requirement.cost * (missing / requirement.quantity);
                items.add(new ShoppingItem(requirement.name, missing, requirement.unit, cost,
                        Aisle.classify(requirement.name), String.join("; ", notes)));
            }
        }

        List<String> warnings = new ArrayList<>();
        if (required.isEmpty()) {
            warnings.add("Meal plan has no meals; nothing to buy.");
        } else if (pantry.isEmpty()) {
            warnings.add("Pantry is empty: all ingredients are listed as missing.");
        }
        return new ShoppingList(items, warnings);
    }

    /** Pantry stock for a requirement in its unit; 0 with an explanatory note if the units can't be compared. */
    private static double available(Requirement requirement, Pantry pantry, List<String> notes) {
        try {
            return pantry.availableQuantity(requirement.name, requirement.unit);
        } catch (IncompatibleUnitException e) {
            String pantryUnit = pantry.getItem(requirement.name)
                    .map(PantryItem::getUnit).map(Unit::getSymbol).orElse("unknown unit");
            notes.add("Pantry stock of " + requirement.name + " is in a unit that cannot be compared ("
                    + pantryUnit + "); full amount listed");
            return 0;
        }
    }

    /**
     * Computes a fresh list from the current plan and pantry. Does not change the cached list or notify.
     *
     * @return the shopping list for the current state
     */
    public ShoppingList generate() {
        return compareAgainstPantry(plan.getRequiredIngredients(), pantry);
    }

    /**
     * Returns the cached list, kept up to date by {@link #onStateChanged}.
     *
     * @return the current shopping list
     */
    public ShoppingList getCurrent() {
        return current;
    }

    /**
     * Called by the plan or the pantry after a change: recomputes the cached list, then notifies this
     * generator's observers once.
     *
     * @param event the change that happened
     */
    @Override
    public void onStateChanged(ChangeEvent event) {
        current = generate();
        int size = current.size();
        notifyObservers("Shopping list updated (" + size + (size == 1 ? " item)" : " items)"));
    }

    /** Unsubscribes from the plan and the pantry; later changes no longer update the cache. Safe to call twice. */
    public void detach() {
        plan.removeObserver(this);
        pantry.removeObserver(this);
    }
}
