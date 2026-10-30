package com.mealmind.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ToolSpec demo() {
        return new ToolSpec("demo_tool", "Does a demo.", List.of(
                ToolParam.required("text", ParamType.STRING, "Some text"),
                ToolParam.optional("count", ParamType.INTEGER, "How many"),
                ToolParam.optional("tags", ParamType.STRING_ARRAY, "Labels")));
    }

    @Test
    void validatesNameAndDescription() {
        assertThrows(IllegalArgumentException.class, () -> new ToolSpec("ab", "x", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ToolSpec("Recipe_search", "x", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ToolSpec("recipe-search", "x", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ToolSpec("recipe_search", " ", List.of()));
    }

    @Test
    void rejectsDuplicateParamsAndCopiesList() {
        assertThrows(IllegalArgumentException.class, () -> new ToolSpec("demo_tool", "x", List.of(
                ToolParam.required("a", ParamType.STRING, "x"), ToolParam.optional("a", ParamType.INTEGER, "y"))));

        List<ToolParam> params = new ArrayList<>(List.of(ToolParam.required("a", ParamType.STRING, "x")));
        ToolSpec spec = new ToolSpec("demo_tool", "x", params);
        params.add(ToolParam.optional("b", ParamType.STRING, "y"));
        assertEquals(1, spec.params().size());
        assertThrows(UnsupportedOperationException.class, () -> spec.params().add(params.get(1)));
    }

    @Test
    void jsonSchemaHasExactStructure() throws Exception {
        JsonNode expected = JSON.readTree("""
                {"name":"demo_tool","description":"Does a demo.",
                 "parameters":{"type":"object",
                   "properties":{
                     "text":{"type":"string","description":"Some text"},
                     "count":{"type":"integer","description":"How many"},
                     "tags":{"type":"array","items":{"type":"string"},"description":"Labels"}},
                   "required":["text"],
                   "additionalProperties":false}}""");

        assertEquals(expected, demo().toJsonSchema());
    }

    @Test
    void requiredIsOmittedWhenNoParamIsRequired() {
        JsonNode schema = new ToolSpec("plan_show", "Shows the plan.", List.of()).toJsonSchema();

        assertFalse(schema.get("parameters").has("required"));
        assertEquals(false, schema.get("parameters").get("additionalProperties").asBoolean());
        assertEquals(0, schema.get("parameters").get("properties").size());
    }

    @Test
    void signatureMarksOptionalParams() {
        assertEquals("demo_tool(text: string, count?: integer, tags?: array)", demo().signature());
        assertEquals("plan_show()", new ToolSpec("plan_show", "x", List.of()).signature());
    }
}
