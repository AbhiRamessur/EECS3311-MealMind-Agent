package com.mealmind.gui.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Whether an AI job started from the GUI is running. Shared by the sections that start AI jobs, so one job
 * disables the others' buttons (the agent facade also rejects overlapping jobs). Used on the UI thread only.
 */
public final class BusyFlag {

    private final List<Runnable> listeners = new ArrayList<>();
    private boolean busy;

    /**
     * Returns whether a job is running.
     *
     * @return {@code true} while busy
     */
    public boolean busy() {
        return busy;
    }

    /**
     * Sets the state and notifies the listeners when it changes.
     *
     * @param value the new state
     */
    public void set(boolean value) {
        if (value != busy) {
            busy = value;
            listeners.forEach(Runnable::run);
        }
    }

    /**
     * Registers a listener that runs on every change.
     *
     * @param listener the listener
     * @throws NullPointerException if {@code listener} is null
     */
    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
