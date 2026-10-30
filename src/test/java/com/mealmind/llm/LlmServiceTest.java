package com.mealmind.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.ChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmServiceTest {

    private LlmService service;
    private List<ChangeEvent> events;

    @BeforeEach
    void setUp() {
        service = new LlmService();
        events = new ArrayList<>();
        service.addObserver(events::add);
    }

    @Test
    void unavailableByDefault() {
        assertFalse(service.isAvailable());
        assertEquals(Optional.of("Not configured"), service.unavailableReason());
        assertEquals(Optional.empty(), service.providerName());

        LlmException e = assertThrows(LlmException.class, () -> service.complete(ScriptedLlmClient.anyRequest()));
        assertEquals(LlmErrorKind.UNAVAILABLE, e.getKind());
        assertEquals("AI features are disabled: Not configured", e.getMessage());
    }

    @Test
    void useClientMakesAvailableAndNotifiesOnce() {
        service.useClient(new ScriptedLlmClient("Ollama", "llama3"));

        assertTrue(service.isAvailable());
        assertEquals(Optional.empty(), service.unavailableReason());
        assertEquals(Optional.of("Ollama"), service.providerName());
        assertEquals(Optional.of("llama3"), service.modelName());
        assertEquals(List.of("AI provider set to Ollama (llama3)"), events.stream().map(ChangeEvent::description).toList());
    }

    @Test
    void completeDelegatesAndRecordsTokens() {
        service.useClient(new ScriptedLlmClient("Fake", "m").reply("Hi", 7, 3));

        assertEquals("Hi", service.complete(ScriptedLlmClient.anyRequest()).content());

        assertEquals(new UsageTracker.Snapshot(1, 0, 7, 3), service.usage().snapshot());
    }

    @Test
    void failuresAreCountedAndRethrownUnchanged() {
        ScriptedLlmClient client = new ScriptedLlmClient("Fake", "m").fail(LlmErrorKind.AUTH);
        service.useClient(client);

        LlmException e = assertThrows(LlmException.class, () -> service.complete(ScriptedLlmClient.anyRequest()));

        assertEquals("scripted AUTH", e.getMessage());
        assertEquals(1, service.usage().snapshot().failedCalls());
    }

    @Test
    void strategyCanBeSwitchedAtRuntime() {
        ScriptedLlmClient a = new ScriptedLlmClient("OpenAI", "gpt-4o").reply("from A", 1, 1);
        ScriptedLlmClient b = new ScriptedLlmClient("Ollama", "llama3").reply("from B", 1, 1);
        service.useClient(a);
        assertEquals("from A", service.complete(ScriptedLlmClient.anyRequest()).content());

        service.useClient(b);

        assertEquals("from B", service.complete(ScriptedLlmClient.anyRequest()).content());
        assertEquals(1, a.calls.size());
        assertEquals(1, b.calls.size());
        assertEquals(2, events.size());
        assertEquals("AI provider set to Ollama (llama3)", events.get(1).description());
        assertSame(service, events.get(1).source());
    }

    @Test
    void disableClearsClientAndRecordsReason() {
        service.useClient(new ScriptedLlmClient("Fake", "m"));

        service.disable("  No key configured ");

        assertFalse(service.isAvailable());
        assertEquals(Optional.of("No key configured"), service.unavailableReason());
        assertEquals(Optional.empty(), service.modelName());
        assertEquals(2, events.size());
        assertThrows(IllegalArgumentException.class, () -> service.disable(" "));
        assertThrows(NullPointerException.class, () -> service.useClient(null));
        assertEquals(2, events.size());
    }
}
