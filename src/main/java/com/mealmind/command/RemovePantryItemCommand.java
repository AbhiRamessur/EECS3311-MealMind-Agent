package com.mealmind.command;

import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Removes an item from the {@link Pantry} (the CLI's {@code pantry remove}). Undo puts the exact
 * removed item back.
 */
public final class RemovePantryItemCommand extends AbstractCommand {

    private final Pantry pantry;
    private final String name;
    private PantryItem removed;

    /**
     * Creates the command.
     *
     * @param pantry the receiver
     * @param name   the item name, matched after normalization
     * @throws NullPointerException if an argument is null
     */
    public RemovePantryItemCommand(Pantry pantry, String name) {
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.name = Objects.requireNonNull(name, "name").trim();
    }

    /**
     * Removes the item.
     *
     * @throws NoSuchElementException if the pantry has no such item; nothing is changed
     */
    @Override
    protected void doExecute() {
        removed = pantry.removeItem(name)
                .orElseThrow(() -> new NoSuchElementException("No pantry item named '" + name + "'"));
    }

    @Override
    protected void doUndo() {
        pantry.replaceItem(removed);
    }

    @Override
    public String description() {
        return "Remove " + name + " from pantry";
    }
}
