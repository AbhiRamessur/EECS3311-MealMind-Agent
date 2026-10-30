package com.mealmind.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;

/** Runs a single tool through a {@link ToolManager}, so tests exercise argument validation too. */
final class ToolTestSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    private ToolTestSupport() {
    }

    private static ToolResult run(Tool tool, String argumentsJson) {
        ToolManager manager = new ToolManager();
        manager.register(tool);
        try {
            return manager.execute(new ToolCall(tool.spec().name(), JSON.readTree(argumentsJson)));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Runs the tool and returns its parsed JSON result, failing the test if it reports an error. */
    static JsonNode ok(Tool tool, String argumentsJson) {
        ToolResult result = run(tool, argumentsJson);
        assertTrue(result.ok(), () -> "Expected success but got: " + result.error());
        try {
            return JSON.readTree(result.json());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Runs the tool and returns its error message, failing the test if it succeeds. */
    static String error(Tool tool, String argumentsJson) {
        ToolResult result = run(tool, argumentsJson);
        assertFalse(result.ok(), () -> "Expected an error but got: " + result.json());
        return result.error();
    }
}
