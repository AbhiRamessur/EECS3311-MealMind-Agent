package com.mealmind.gui.logic;

import com.mealmind.app.ApplicationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Observer bindings to the <em>current</em> meal plan and shopping-list generator. Both are replaced by
 * {@code ApplicationContext.newPlan}, so after a replacement {@link #repoint()} closes the old bindings, binds
 * the same refresh actions to the new objects (read from the context, never cached) and runs them once.
 *
 * <p>The context sends no notification when the plan is replaced, so whoever replaces it must call
 * {@link #repoint()}; in this application only the GUI itself replaces the plan while the window is open.
 */
public final class PlanBindings implements AutoCloseable {

    private final ApplicationContext ctx;
    private final UiThread ui;
    private final List<Runnable> planActions = new ArrayList<>();
    private final List<Runnable> shoppingActions = new ArrayList<>();
    private final List<ObserverBinding> bindings = new ArrayList<>();

    /**
     * Creates the bindings holder.
     *
     * @param ctx the session
     * @param ui  where the refresh actions run
     * @throws NullPointerException if an argument is null
     */
    public PlanBindings(ApplicationContext ctx, UiThread ui) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    /**
     * Runs {@code action} whenever the current plan changes (and after a replacement).
     *
     * @param action the refresh action
     */
    public void onPlanChange(Runnable action) {
        planActions.add(Objects.requireNonNull(action, "action"));
        bindings.add(ObserverBinding.bind(ctx.plan(), ui, action));
    }

    /**
     * Runs {@code action} whenever the current shopping list changes (and after a replacement).
     *
     * @param action the refresh action
     */
    public void onShoppingChange(Runnable action) {
        shoppingActions.add(Objects.requireNonNull(action, "action"));
        bindings.add(ObserverBinding.bind(ctx.generator(), ui, action));
    }

    /** Moves every binding to the context's current plan and generator, then runs every action once. */
    public void repoint() {
        closeBindings();
        planActions.forEach(action -> bindings.add(ObserverBinding.bind(ctx.plan(), ui, action)));
        shoppingActions.forEach(action -> bindings.add(ObserverBinding.bind(ctx.generator(), ui, action)));
        planActions.forEach(action -> ui.run(action));
        shoppingActions.forEach(action -> ui.run(action));
    }

    /**
     * Returns how many bindings are open (for tests).
     *
     * @return the number of open bindings
     */
    public int size() {
        return bindings.size();
    }

    /** Closes every binding; actions stay registered, so {@link #repoint()} can bind them again. */
    @Override
    public void close() {
        closeBindings();
    }

    private void closeBindings() {
        bindings.forEach(ObserverBinding::close);
        bindings.clear();
    }
}
