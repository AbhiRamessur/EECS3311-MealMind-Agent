package com.mealmind.command;

/**
 * The Command role of the <strong>Command pattern</strong>: one undoable change to the model.
 *
 * <p>Roles: {@code Command} is the Command interface; {@link AddPantryItemCommand},
 * {@link RemovePantryItemCommand}, {@link ReplaceMealCommand} and {@link ScaleRecipeCommand} are
 * ConcreteCommands that hold their parameters and call the receiver; {@link CommandHistory} is the
 * Invoker, which runs commands and keeps the undo and redo stacks; {@code Pantry},
 * {@code MealPlan} and {@code RecipeBook} are the Receivers that hold the actual state. Callers
 * (the CLI, GUI and agent) build a command and hand it to the history instead of changing the
 * model directly, so every change can be undone and redone.
 *
 * <p>Contract for implementations: {@link #execute()} may be called again after {@link #undo()}
 * (that is how redo works); calling {@code execute()} twice in a row, or {@code undo()} before
 * {@code execute()}, throws {@link IllegalStateException}; if {@code execute()} throws, the
 * receiver is unchanged and the command stays not-executed.
 */
public interface Command {

    /**
     * Applies the change to the receiver.
     *
     * @throws IllegalStateException if the command is already executed
     */
    void execute();

    /**
     * Reverses the last {@link #execute()}, restoring the receiver's previous state.
     *
     * @throws IllegalStateException if the command is not currently executed
     */
    void undo();

    /**
     * Returns a short human-readable summary, e.g. {@code "Add Rice to pantry"}.
     *
     * @return the description
     */
    String description();
}
