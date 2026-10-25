package com.mealmind.command;

import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.Objects;
import java.util.Optional;

/**
 * Adds stock to the {@link Pantry} (F02 / UC02), merging with an existing entry of the same name.
 * Undo restores the entry exactly as it was before, or removes it if it was new.
 */
public final class AddPantryItemCommand extends AbstractCommand {

    private final Pantry pantry;
    private final PantryItem item;
    private Optional<PantryItem> previous = Optional.empty();

    /**
     * Creates the command.
     *
     * @param pantry the receiver
     * @param item   the stock to add
     * @throws NullPointerException if an argument is null
     */
    public AddPantryItemCommand(Pantry pantry, PantryItem item) {
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.item = Objects.requireNonNull(item, "item");
    }

    @Override
    protected void doExecute() {
        // Pantry.addItem throws before changing anything if the units are incompatible.
        previous = pantry.addItem(item);
    }

    @Override
    protected void doUndo() {
        if (previous.isPresent()) {
            pantry.replaceItem(previous.get());
        } else {
            pantry.removeItem(item.getName());
        }
    }

    @Override
    public String description() {
        return "Add " + item.getName() + " to pantry";
    }
}
