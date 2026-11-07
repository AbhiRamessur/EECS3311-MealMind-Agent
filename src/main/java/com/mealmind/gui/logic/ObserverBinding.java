package com.mealmind.gui.logic;

import com.mealmind.model.ModelObserver;
import com.mealmind.model.ObservableSubject;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The GUI side of the Observer pattern: subscribes a view's refresh action to a model subject. Every
 * notification is forwarded through a {@link UiThread}, so the view is only touched on the UI thread and
 * the model never knows about the GUI. Closing the binding unsubscribes it.
 */
public final class ObserverBinding implements AutoCloseable {

    private final ObservableSubject subject;
    private final ModelObserver observer;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ObserverBinding(ObservableSubject subject, ModelObserver observer) {
        this.subject = subject;
        this.observer = observer;
    }

    /**
     * Subscribes {@code onChange} to {@code subject}.
     *
     * @param subject  the model to watch
     * @param ui       where {@code onChange} runs
     * @param onChange the view's refresh action
     * @return the binding; close it to unsubscribe
     * @throws NullPointerException if an argument is null
     */
    public static ObserverBinding bind(ObservableSubject subject, UiThread ui, Runnable onChange) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(ui, "ui");
        Objects.requireNonNull(onChange, "onChange");
        ModelObserver observer = event -> ui.run(onChange);
        subject.addObserver(observer);
        return new ObserverBinding(subject, observer);
    }

    /** Unsubscribes; calling it again has no effect. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            subject.removeObserver(observer);
        }
    }
}
