package com.mealmind.model;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Subject role of the <strong>Observer pattern</strong>.
 *
 * <p>Roles: {@code ObservableSubject} is the Subject (keeps the observer list and sends
 * notifications); {@link ModelObserver} is the Observer interface; subclasses such as
 * {@link DayPlan}, {@link MealPlan} and {@code Pantry} are ConcreteSubjects that call
 * {@link #notifyObservers(String)} after each successful change; views and the shopping-list
 * generator will be ConcreteObservers. Subjects know nothing about their observers beyond this
 * interface, so the model never depends on the GUI or CLI.
 *
 * <p><strong>Notification policy:</strong>
 * <ul>
 *   <li>Notification is synchronous, on the thread that made the change, in registration order.
 *       The JavaFX GUI must therefore wrap UI updates in {@code Platform.runLater} when a change
 *       happens off the JavaFX application thread.</li>
 *   <li>The observer list is a {@link CopyOnWriteArrayList}, so an observer may add or remove
 *       observers (including itself) from inside its callback; each notification uses the list as it
 *       was when the notification started.</li>
 *   <li>If an observer throws a {@link RuntimeException}, the remaining observers are still
 *       notified. Afterwards the first exception is rethrown, with any later ones attached via
 *       {@link Throwable#addSuppressed}. The subject's change has already happened at that point.</li>
 * </ul>
 */
public abstract class ObservableSubject {

    private final CopyOnWriteArrayList<ModelObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * Registers an observer. Registering the same observer again has no effect.
     *
     * @param observer the observer to add
     * @throws NullPointerException if {@code observer} is null
     */
    public void addObserver(ModelObserver observer) {
        observers.addIfAbsent(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * Unregisters an observer. Does nothing if it was not registered.
     *
     * @param observer the observer to remove
     * @throws NullPointerException if {@code observer} is null
     */
    public void removeObserver(ModelObserver observer) {
        observers.remove(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * Returns how many observers are registered.
     *
     * @return the observer count
     */
    public int observerCount() {
        return observers.size();
    }

    /**
     * Notifies every registered observer of a change, following the policy in the class Javadoc.
     *
     * @param description a short summary of the change
     * @throws RuntimeException the first exception thrown by an observer, with later ones suppressed
     */
    protected void notifyObservers(String description) {
        ChangeEvent event = new ChangeEvent(this, description);
        RuntimeException first = null;
        for (ModelObserver observer : observers) {
            try {
                observer.onStateChanged(event);
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                } else if (e != first) {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
