package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryingLlmClientTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final RetryingLlmClient.Sleeper recordingSleeper = sleeps::add;

    private RetryingLlmClient retrying(ScriptedLlmClient delegate, int maxAttempts) {
        return new RetryingLlmClient(delegate, maxAttempts, Duration.ofMillis(100), recordingSleeper);
    }

    @Test
    void retryableFailureThenSuccess() {
        ScriptedLlmClient delegate = new ScriptedLlmClient("Fake", "m").fail(LlmErrorKind.TIMEOUT).reply("OK", 1, 1);

        LlmResponse response = retrying(delegate, 2).complete(ScriptedLlmClient.anyRequest());

        assertEquals("OK", response.content());
        assertEquals(2, delegate.calls.size());
        assertEquals(List.of(Duration.ofMillis(100)), sleeps);
    }

    @Test
    void nonRetryableFailureIsThrownImmediately() {
        ScriptedLlmClient delegate = new ScriptedLlmClient("Fake", "m").fail(LlmErrorKind.AUTH).reply("OK", 1, 1);

        LlmException e = assertThrows(LlmException.class, () -> retrying(delegate, 3).complete(ScriptedLlmClient.anyRequest()));

        assertEquals(LlmErrorKind.AUTH, e.getKind());
        assertEquals(1, delegate.calls.size());
        assertEquals(List.of(), sleeps);
    }

    @Test
    void lastExceptionIsThrownAfterAllAttempts() {
        ScriptedLlmClient delegate = new ScriptedLlmClient("Fake", "m")
                .fail(LlmErrorKind.SERVER_ERROR).fail(LlmErrorKind.UNREACHABLE);

        LlmException e = assertThrows(LlmException.class, () -> retrying(delegate, 2).complete(ScriptedLlmClient.anyRequest()));

        assertEquals(LlmErrorKind.UNREACHABLE, e.getKind());
        assertEquals(2, delegate.calls.size());
    }

    @Test
    void firstAttemptSuccessNeverSleeps() {
        ScriptedLlmClient delegate = new ScriptedLlmClient("Fake", "m").reply("OK", 1, 1);

        retrying(delegate, 2).complete(ScriptedLlmClient.anyRequest());

        assertEquals(List.of(), sleeps);
    }

    @Test
    void backoffGrowsLinearly() {
        ScriptedLlmClient delegate = new ScriptedLlmClient("Fake", "m")
                .fail(LlmErrorKind.RATE_LIMIT).fail(LlmErrorKind.RATE_LIMIT).reply("OK", 1, 1);

        retrying(delegate, 3).complete(ScriptedLlmClient.anyRequest());

        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200)), sleeps);
    }

    @Test
    void interruptDuringSleepRestoresFlagAndThrowsInterrupted() {
        ScriptedLlmClient delegate = new ScriptedLlmClient("Fake", "m").fail(LlmErrorKind.TIMEOUT).reply("OK", 1, 1);
        RetryingLlmClient client = new RetryingLlmClient(delegate, 2, Duration.ofMillis(100), d -> {
            throw new InterruptedException("stop");
        });
        try {
            LlmException e = assertThrows(LlmException.class, () -> client.complete(ScriptedLlmClient.anyRequest()));

            assertEquals(LlmErrorKind.INTERRUPTED, e.getKind());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, delegate.calls.size());
        } finally {
            Thread.interrupted(); // clear the flag so other tests are unaffected
        }
    }

    @Test
    void invalidArgumentsAreRejectedAndNamesDelegate() {
        ScriptedLlmClient delegate = new ScriptedLlmClient("Fake", "fake-model");

        assertThrows(IllegalArgumentException.class, () -> retrying(delegate, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryingLlmClient(delegate, 2, Duration.ofMillis(-1), recordingSleeper));
        assertThrows(NullPointerException.class, () -> new RetryingLlmClient(null));

        RetryingLlmClient client = new RetryingLlmClient(delegate);
        assertEquals("Fake", client.providerName());
        assertEquals("fake-model", client.modelName());
    }
}
