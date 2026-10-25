package com.mealmind.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservableSubjectTest {

    /** Minimal ConcreteSubject for testing. */
    private static final class TestSubject extends ObservableSubject {
        void change(String description) {
            notifyObservers(description);
        }
    }

    /** Observer that records its name into a shared log. */
    private static ModelObserver recorder(String name, List<String> log) {
        return event -> log.add(name + ":" + event.description());
    }

    @Test
    void notifiesObserversInRegistrationOrder() {
        TestSubject subject = new TestSubject();
        List<String> log = new ArrayList<>();
        subject.addObserver(recorder("a", log));
        subject.addObserver(recorder("b", log));
        subject.addObserver(recorder("c", log));

        subject.change("x");

        assertEquals(List.of("a:x", "b:x", "c:x"), log);
    }

    @Test
    void eventCarriesSourceAndDescription() {
        TestSubject subject = new TestSubject();
        List<ChangeEvent> events = new ArrayList<>();
        subject.addObserver(events::add);

        subject.change("something changed");

        assertEquals(1, events.size());
        assertSame(subject, events.get(0).source());
        assertEquals("something changed", events.get(0).description());
    }

    @Test
    void duplicateRegistrationIsIgnored() {
        TestSubject subject = new TestSubject();
        List<String> log = new ArrayList<>();
        ModelObserver observer = recorder("a", log);
        subject.addObserver(observer);
        subject.addObserver(observer);

        subject.change("x");

        assertEquals(1, subject.observerCount());
        assertEquals(List.of("a:x"), log);
    }

    @Test
    void removedObserversAreNotNotified() {
        TestSubject subject = new TestSubject();
        List<String> log = new ArrayList<>();
        ModelObserver a = recorder("a", log);
        subject.addObserver(a);
        subject.addObserver(recorder("b", log));
        subject.removeObserver(a);

        subject.change("x");

        assertEquals(1, subject.observerCount());
        assertEquals(List.of("b:x"), log);
    }

    @Test
    void observerCanRemoveItselfDuringCallback() {
        TestSubject subject = new TestSubject();
        List<String> log = new ArrayList<>();
        ModelObserver selfRemoving = new ModelObserver() {
            @Override
            public void onStateChanged(ChangeEvent event) {
                log.add("once:" + event.description());
                subject.removeObserver(this);
            }
        };
        subject.addObserver(selfRemoving);
        subject.addObserver(recorder("b", log));

        subject.change("1");
        subject.change("2");

        assertEquals(List.of("once:1", "b:1", "b:2"), log);
        assertEquals(1, subject.observerCount());
    }

    @Test
    void failingObserversDoNotStopOthersAndFirstExceptionIsRethrown() {
        TestSubject subject = new TestSubject();
        List<String> log = new ArrayList<>();
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");
        subject.addObserver(event -> {
            throw first;
        });
        subject.addObserver(recorder("b", log));
        subject.addObserver(event -> {
            throw second;
        });
        subject.addObserver(recorder("d", log));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> subject.change("x"));

        assertSame(first, thrown);
        assertArrayEquals(new Throwable[] {second}, thrown.getSuppressed());
        assertEquals(List.of("b:x", "d:x"), log);
    }

    @Test
    void nullObserverIsRejected() {
        TestSubject subject = new TestSubject();

        assertThrows(NullPointerException.class, () -> subject.addObserver(null));
        assertThrows(NullPointerException.class, () -> subject.removeObserver(null));
    }

    @Test
    void changeEventRejectsNullSourceAndBlankDescription() {
        TestSubject subject = new TestSubject();

        assertThrows(NullPointerException.class, () -> new ChangeEvent(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeEvent(subject, " "));
        assertThrows(IllegalArgumentException.class, () -> new ChangeEvent(subject, null));
    }
}
