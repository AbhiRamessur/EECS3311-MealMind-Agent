package com.mealmind.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolManagerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** A tool that echoes its validated arguments and counts runs. */
    private static final class EchoTool implements Tool {
        int runs;

        @Override
        public ToolSpec spec() {
            return new ToolSpec("echo_tool", "Echoes its arguments.", List.of(
                    ToolParam.required("text", ParamType.STRING, "Text to echo"),
                    ToolParam.optional("count", ParamType.INTEGER, "A whole number"),
                    ToolParam.optional("ratio", ParamType.NUMBER, "A number"),
                    ToolParam.optional("flag", ParamType.BOOLEAN, "A flag"),
                    ToolParam.optional("tags", ParamType.STRING_ARRAY, "Labels")));
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            runs++;
            return arguments;
        }
    }

    /** A tool whose behaviour is a function of its (empty) arguments. */
    private record FuncTool(String name, Function<JsonNode, JsonNode> body) implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec(name, "Test tool " + name + ".", List.of());
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            return body.apply(arguments);
        }
    }

    private ToolManager manager;
    private EchoTool echo;

    @BeforeEach
    void setUp() {
        manager = new ToolManager();
        echo = new EchoTool();
        manager.register(echo);
    }

    private ToolResult run(String tool, String argumentsJson) throws Exception {
        return manager.execute(new ToolCall(tool, JSON.readTree(argumentsJson)));
    }

    private String error(String argumentsJson) throws Exception {
        ToolResult result = run("echo_tool", argumentsJson);
        assertFalse(result.ok(), result.toString());
        assertEquals(0, echo.runs, "the tool must not run on invalid arguments");
        return result.error();
    }

    // ---- registration and description ----

    @Test
    void registerFindAndRejectDuplicates() {
        assertTrue(manager.find("echo_tool").isPresent());
        assertTrue(manager.find("nope").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> manager.register(new EchoTool()));
        assertThrows(NullPointerException.class, () -> manager.register(null));
    }

    @Test
    void specsAndSchemasAreSortedByName() {
        manager.register(new FuncTool("zeta_tool", args -> args));
        manager.register(new FuncTool("alpha_tool", args -> args));

        assertEquals(List.of("alpha_tool", "echo_tool", "zeta_tool"), manager.specs().stream().map(ToolSpec::name).toList());
        assertEquals(List.of("alpha_tool", "echo_tool", "zeta_tool"), manager.schemas().findValuesAsText("name"));
    }

    @Test
    void describeForPromptExactText() {
        ToolManager small = new ToolManager();
        small.register(new FuncTool("plan_show", args -> args));
        small.register(new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("find_it", "Finds it.", List.of(
                        ToolParam.required("name", ParamType.STRING, "What to find"),
                        ToolParam.optional("limit", ParamType.INTEGER, "Max results")));
            }

            @Override
            public JsonNode execute(JsonNode arguments) {
                return arguments;
            }
        });

        assertEquals("""
                - find_it(name: string, limit?: integer): Finds it.
                    name (string, required): What to find
                    limit (integer, optional): Max results
                - plan_show(): Test tool plan_show.""", small.describeForPrompt());
    }

    // ---- parsing model output ----

    @Test
    void parsesBareFencedAndEmbeddedJson() {
        ToolCall bare = manager.parse("{\"tool\":\"echo_tool\",\"arguments\":{\"text\":\"hi\"}}");
        assertEquals("echo_tool", bare.tool());
        assertEquals("hi", bare.arguments().get("text").asText());

        ToolCall fencedJson = manager.parse("Here you go:\n```json\n{\"tool\": \"plan_show\", \"arguments\": {}}\n```\nDone.");
        assertEquals("plan_show", fencedJson.tool());

        ToolCall fencedPlain = manager.parse("```\n{\"tool\": \"plan_show\"}\n```");
        assertEquals("plan_show", fencedPlain.tool());

        ToolCall prose = manager.parse("I will check the pantry now. {\"tool\": \"pantry_query\", \"arguments\": {}} Thanks!");
        assertEquals("pantry_query", prose.tool());
    }

    @Test
    void bracesAndEscapedQuotesInsideStringsDoNotConfuseExtraction() {
        ToolCall call = manager.parse("Sure: {\"tool\": \"echo_tool\", \"arguments\": {\"text\": \"a } { \\\" tricky\"}} trailing }");

        assertEquals("a } { \" tricky", call.arguments().get("text").asText());
    }

    @Test
    void missingOrNullArgumentsBecomeEmptyObject() {
        assertTrue(manager.parse("{\"tool\": \"plan_show\"}").arguments().isEmpty());
        assertTrue(manager.parse("{\"tool\": \"plan_show\", \"arguments\": null}").arguments().isObject());
        assertTrue(manager.parse("{\"tool\": \"plan_show\", \"arguments\": null}").arguments().isEmpty());
    }

    @Test
    void parseErrorsExplainTheProblem() {
        assertEquals("No JSON object found in the reply",
                assertThrows(ToolParseException.class, () -> manager.parse("I think you should eat pasta.")).getMessage());
        assertEquals("No JSON object found in the reply",
                assertThrows(ToolParseException.class, () -> manager.parse("42")).getMessage());
        assertEquals("The JSON must contain a string field named 'tool'",
                assertThrows(ToolParseException.class, () -> manager.parse("{\"name\": \"plan_show\"}")).getMessage());
        assertEquals("The JSON must contain a string field named 'tool'",
                assertThrows(ToolParseException.class, () -> manager.parse("{\"tool\": 5}")).getMessage());
        assertEquals("'arguments' must be a JSON object",
                assertThrows(ToolParseException.class, () -> manager.parse("{\"tool\": \"x_y\", \"arguments\": [1]}")).getMessage());
        assertThrows(ToolParseException.class, () -> manager.parse("  "));
        assertThrows(ToolParseException.class, () -> manager.parse(null));
        assertTrue(assertThrows(ToolParseException.class, () -> manager.parse("{tool: plan_show}")).getMessage()
                .startsWith("The reply contains invalid JSON"));
    }

    // ---- executing calls ----

    @Test
    void unknownToolListsAvailableNames() throws Exception {
        manager.register(new FuncTool("alpha_tool", args -> args));

        ToolResult result = run("nope", "{}");

        assertFalse(result.ok());
        assertEquals("Unknown tool 'nope'. Available tools: alpha_tool, echo_tool", result.error());
    }

    @Test
    void argumentValidationErrors() throws Exception {
        assertEquals("Missing required argument 'text'", error("{}"));
        assertEquals("Missing required argument 'text'", error("{\"text\": null}"));
        assertEquals("Unknown argument 'colour' for tool echo_tool", error("{\"text\": \"a\", \"colour\": \"red\"}"));
        assertEquals("Argument 'count' must be an integer", error("{\"text\": \"a\", \"count\": \"5\"}"));
        assertEquals("Argument 'count' must be an integer", error("{\"text\": \"a\", \"count\": 3.5}"));
        assertEquals("Argument 'count' must be an integer", error("{\"text\": \"a\", \"count\": 99999999999}"));
        assertEquals("Argument 'flag' must be a boolean (true or false)", error("{\"text\": \"a\", \"flag\": \"true\"}"));
        assertEquals("Argument 'text' must be a string", error("{\"text\": 7}"));
        assertEquals("Argument 'tags' must be an array of at most 20 non-blank strings",
                error("{\"text\": \"a\", \"tags\": [\"x\", 3]}"));
        assertEquals("Argument 'tags' must be an array of at most 20 non-blank strings",
                error("{\"text\": \"a\", \"tags\": [\"x\", \"  \"]}"));
        String twentyOne = "[" + "\"x\",".repeat(20) + "\"x\"]";
        assertEquals("Argument 'tags' must be an array of at most 20 non-blank strings",
                error("{\"text\": \"a\", \"tags\": " + twentyOne + "}"));
    }

    @Test
    void validCallRunsWithCleanedArguments() throws Exception {
        ToolResult result = run("echo_tool",
                "{\"text\": \"  hi  \", \"count\": 3, \"ratio\": 1.5, \"flag\": true, \"tags\": [\" a \", \"b\"]}");

        assertTrue(result.ok(), result.toString());
        assertEquals(1, echo.runs);
        assertEquals(JSON.readTree("{\"text\":\"hi\",\"count\":3,\"ratio\":1.5,\"flag\":true,\"tags\":[\"a\",\"b\"]}"),
                JSON.readTree(result.json()));
        assertFalse(result.json().contains("\n"), "result JSON is compact");
    }

    @Test
    void nullOptionalArgumentIsTreatedAsAbsent() throws Exception {
        ToolResult result = run("echo_tool", "{\"text\": \"hi\", \"count\": null}");

        assertTrue(result.ok());
        assertFalse(JSON.readTree(result.json()).has("count"));
    }

    @Test
    void toolExceptionsBecomeErrorResults() throws Exception {
        manager.register(new FuncTool("bad_arg", args -> {
            throw new IllegalArgumentException("limit must be between 1 and 10, got 50");
        }));
        manager.register(new FuncTool("no_such", args -> {
            throw new NoSuchElementException("No recipe with id 'R999'");
        }));
        manager.register(new FuncTool("null_msg", args -> {
            throw new RuntimeException();
        }));
        manager.register(new FuncTool("long_msg", args -> {
            throw new IllegalStateException("x".repeat(500));
        }));

        assertEquals("limit must be between 1 and 10, got 50", run("bad_arg", "{}").error());
        assertEquals("No recipe with id 'R999'", run("no_such", "{}").error());
        ToolResult nullMessage = run("null_msg", "{}");
        assertFalse(nullMessage.ok());
        assertEquals("RuntimeException", nullMessage.error());
        String truncated = run("long_msg", "{}").error();
        assertEquals(300, truncated.length());
        assertTrue(truncated.endsWith("…"));
        assertFalse(truncated.contains("at com.mealmind"), "no stack trace");
    }

    @Test
    void oversizedResultIsRejected() throws Exception {
        manager.register(new FuncTool("big_tool", args -> {
            ObjectNode node = JSON.createObjectNode();
            node.put("data", "x".repeat(9000));
            return node;
        }));

        ToolResult result = run("big_tool", "{}");

        assertFalse(result.ok());
        assertTrue(result.error().startsWith("Result too large ("), result.error());
        assertTrue(result.error().endsWith("characters); narrow the query (use a smaller limit or more specific arguments)"));
    }

    @Test
    void executionCountCountsSuccessesAndFailures() throws Exception {
        run("echo_tool", "{\"text\": \"ok\"}");
        run("echo_tool", "{}");
        run("nope", "{}");

        assertEquals(3, manager.executionCount());
    }
}
