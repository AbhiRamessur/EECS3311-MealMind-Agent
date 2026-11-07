package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.llm.LLMClient;
import com.mealmind.llm.LlmErrorKind;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.LlmService;
import org.junit.jupiter.api.Test;

class AiStatusModelTest {

    /** A client that is never called; only its names are shown. */
    private static final class NamedClient implements LLMClient {
        @Override
        public String providerName() {
            return "Ollama";
        }

        @Override
        public String modelName() {
            return "llama3";
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            throw new LlmException(LlmErrorKind.UNAVAILABLE, "not used in this test", null);
        }
    }

    @Test
    void disabledShowsAShortReason() {
        LlmService llm = new LlmService();
        llm.disable("No OpenAI API key configured");
        AiStatusModel model = new AiStatusModel(llm);

        assertFalse(model.available());
        assertEquals("AI disabled: No OpenAI API key configured", model.text());
    }

    @Test
    void longReasonsAreCut() {
        LlmService llm = new LlmService();
        llm.disable("x".repeat(100));

        assertEquals("AI disabled: " + "x".repeat(80) + "...", new AiStatusModel(llm).text());
    }

    @Test
    void availableShowsProviderAndModelOnly() {
        LlmService llm = new LlmService();
        llm.useClient(new NamedClient());
        AiStatusModel model = new AiStatusModel(llm);

        assertTrue(model.available());
        assertEquals("AI: Ollama (llama3)", model.text());
        assertFalse(model.text().matches(".*(sk-|LLM_API_KEY|Bearer|http).*"));
    }
}
