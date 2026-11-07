package com.mealmind.gui.logic;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs slow jobs (later: AI calls) off the UI thread, one at a time in submission order, and delivers each
 * result or exception back through a {@link UiThread}, so the window never freezes. Backed by one daemon
 * thread, so it never keeps the application alive.
 */
public final class BackgroundRunner implements AutoCloseable {

    private final UiThread ui;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mealmind-background");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Creates a runner.
     *
     * @param ui where results are delivered
     * @throws NullPointerException if {@code ui} is null
     */
    public BackgroundRunner(UiThread ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    /**
     * Runs {@code job} in the background.
     *
     * @param job       the work
     * @param onSuccess receives the result on the UI thread
     * @param onError   receives the exception on the UI thread
     * @param <T>       the result type
     * @throws IllegalStateException if the runner is closed
     * @throws NullPointerException  if an argument is null
     */
    public <T> void submit(Callable<T> job, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onError, "onError");
        if (executor.isShutdown()) {
            throw new IllegalStateException("The background runner is closed");
        }
        executor.execute(() -> {
            Runnable delivery;
            try {
                T result = job.call();
                delivery = () -> onSuccess.accept(result);
            } catch (Exception e) {
                delivery = () -> onError.accept(e);
            }
            try {
                ui.run(delivery);
            } catch (RuntimeException ignored) {
                // A broken callback must not stop later jobs.
            }
        });
    }

    /** Stops accepting jobs; queued jobs still finish. Calling it again has no effect. */
    @Override
    public void close() {
        executor.shutdown();
    }
}
