package com.mealmind.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ParamTypeTest {

    @Test
    void jsonTypeNames() {
        assertEquals(List.of("string", "integer", "number", "boolean", "array"),
                List.of(ParamType.values()).stream().map(ParamType::jsonType).toList());
    }
}
