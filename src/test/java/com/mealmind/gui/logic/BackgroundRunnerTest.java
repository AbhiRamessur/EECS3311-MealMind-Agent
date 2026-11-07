package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BackgroundRunnerTest {

    /** Records that each delivery went through the UI thread. */
    private final List<String> uiLog = new CopyOnWriteArrayList<>();
    private final BackgroundRunner runner = new BackgroundRunner(task -> {
        uiLog.add("ui");
        task.run();
    });

    @AfterEach
    void tearDown() {
        runner.close();
    }

    @Test
    void successIsDeliveredThroughTheUiThreadFromAnotherThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<String> results = new CopyOnWriteArrayList<>();
        String caller = Thread.currentThread().getName();

        runner.submit(() -> Thread.currentThread().getName(), name -> {
            results.add(name);
            done.countDown();
        }, error -> done.countDown());

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNotEquals(caller, results.get(0));
        assertEquals("mealmind-background", results.get(0));
        assertEquals(List.of("ui"), uiLog);
    }

    @Test
    void exceptionGoesToOnError() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        runner.<String>submit(() -> {
            throw new IllegalStateException("boom");
        }, value -> done.countDown(), error -> {
            errors.add(error);
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals("boom", errors.get(0).getMessage());
    }

    @Test
    void throwingCallbackDoesNotStopLaterJobsAndOrderIsKept() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<Integer> order = new CopyOnWriteArrayList<>();

        runner.submit(() -> 1, value -> {
            throw new IllegalArgumentException("broken view");
        }, error -> { });
        for (int i = 2; i <= 4; i++) {
            int n = i;
            runner.submit(() -> n, order::add, error -> { });
        }
        runner.submit(() -> 5, value -> done.countDown(), error -> { });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(2, 3, 4), order);
    }

    @Test
    void submitAfterCloseThrowsAndCloseIsIdempotent() {
        runner.close();
        runner.close();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> runner.submit(() -> 1, value -> { }, error -> { }));
        assertEquals("The background runner is closed", e.getMessage());
    }
}
