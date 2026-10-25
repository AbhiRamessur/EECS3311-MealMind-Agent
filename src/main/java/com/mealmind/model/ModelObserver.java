package com.mealmind.model;

/**
 * The Observer role of the Observer pattern: anything that wants to react when an
 * {@link ObservableSubject} changes, such as a view or the shopping-list generator.
 */
@FunctionalInterface
public interface ModelObserver {

    /**
     * Called after the subject has changed. Runs synchronously on the thread that made the change.
     *
     * @param event what changed
     */
    void onStateChanged(ChangeEvent event);
}
