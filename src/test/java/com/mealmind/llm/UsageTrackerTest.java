package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UsageTrackerTest {

    @Test
    void countsAccumulateAcrossThreads() throws InterruptedException {
        UsageTracker tracker = new UsageTracker();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int thread = 0; thread < 8; thread++) {
            pool.submit(() -> {
                for (int call = 0; call < 100; call++) {
                    tracker.recordSuccess(new TokenUsage(3, 2));
                    tracker.recordFailure();
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        UsageTracker.Snapshot snapshot = tracker.snapshot();

        assertEquals(new UsageTracker.Snapshot(800, 800, 2400, 1600), snapshot);
        assertEquals(4000, snapshot.totalTokens());
    }

    @Test
    void resetAndSnapshotIsIndependent() {
        UsageTracker tracker = new UsageTracker();
        tracker.recordSuccess(new TokenUsage(10, 5));
        UsageTracker.Snapshot before = tracker.snapshot();

        tracker.reset();

        assertEquals(new UsageTracker.Snapshot(1, 0, 10, 5), before);
        assertEquals(new UsageTracker.Snapshot(0, 0, 0, 0), tracker.snapshot());
    }
}
