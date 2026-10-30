package com.mealmind.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolParamTest {

    @Test
    void acceptsCamelCaseNames() {
        assertDoesNotThrow(() -> ToolParam.required("q", ParamType.STRING, "Query"));
        assertDoesNotThrow(() -> ToolParam.optional("maxPrepMinutes", ParamType.INTEGER, "Limit"));
        assertTrue(ToolParam.required("a", ParamType.STRING, "x").required());
        assertFalse(ToolParam.optional("a", ParamType.STRING, "x").required());
    }

    @Test
    void rejectsInvalidNamesTypesAndDescriptions() {
        assertThrows(IllegalArgumentException.class, () -> ToolParam.required("MaxPrep", ParamType.STRING, "x"));
        assertThrows(IllegalArgumentException.class, () -> ToolParam.required("max_prep", ParamType.STRING, "x"));
        assertThrows(IllegalArgumentException.class, () -> ToolParam.required("1abc", ParamType.STRING, "x"));
        assertThrows(IllegalArgumentException.class, () -> ToolParam.required("a" + "b".repeat(31), ParamType.STRING, "x"));
        assertThrows(IllegalArgumentException.class, () -> ToolParam.required(null, ParamType.STRING, "x"));
        assertThrows(IllegalArgumentException.class, () -> ToolParam.required("ok", null, "x"));
        assertThrows(IllegalArgumentException.class, () -> ToolParam.required("ok", ParamType.STRING, " "));
    }
}
