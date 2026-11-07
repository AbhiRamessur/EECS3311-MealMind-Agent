package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mealmind.model.ObservableSubject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObserverBindingTest {

    /** A subject the test can fire. */
    private static final class Subject extends ObservableSubject {
        void fire(String description) {
            notifyObservers(description);
        }
    }

    /** Records each task it is given, then runs it, so forwarding through the UI thread is visible. */
    private static final class RecordingUiThread implements UiThread {
        final List<String> log = new ArrayList<>();

        @Override
        public void run(Runnable task) {
            log.add("ui");
            task.run();
        }
    }

    @Test
    void forwardsEveryNotificationThroughTheUiThreadInOrder() {
        Subject subject = new Subject();
        RecordingUiThread ui = new RecordingUiThread();
        int[] count = {0};

        ObserverBinding.bind(subject, ui, () -> ui.log.add("change " + ++count[0]));
        subject.fire("a");
        subject.fire("b");

        assertEquals(List.of("ui", "change 1", "ui", "change 2"), ui.log);
    }

    @Test
    void notificationsFromAnotherThreadAreForwarded() throws Exception {
        Subject subject = new Subject();
        RecordingUiThread ui = new RecordingUiThread();
        List<String> threads = new ArrayList<>();
        ObserverBinding.bind(subject, ui, () -> threads.add(Thread.currentThread().getName()));

        Thread worker = new Thread(() -> subject.fire("from worker"), "worker");
        worker.start();
        worker.join(5000);

        assertEquals(List.of("ui"), ui.log.subList(0, 1));
        assertEquals(List.of("worker"), threads);
    }

    @Test
    void closeStopsDeliveryAndIsIdempotent() {
        Subject subject = new Subject();
        int[] count = {0};
        ObserverBinding binding = ObserverBinding.bind(subject, new ImmediateUiThread(), () -> count[0]++);

        subject.fire("a");
        binding.close();
        binding.close();
        subject.fire("b");

        assertEquals(1, count[0]);
        assertEquals(0, subject.observerCount());
    }

    @Test
    void bindingsAreIndependent() {
        Subject subject = new Subject();
        int[] first = {0};
        int[] second = {0};
        ObserverBinding one = ObserverBinding.bind(subject, new ImmediateUiThread(), () -> first[0]++);
        ObserverBinding.bind(subject, new ImmediateUiThread(), () -> second[0]++);

        subject.fire("a");
        one.close();
        subject.fire("b");

        assertEquals(1, first[0]);
        assertEquals(2, second[0]);
    }
}
