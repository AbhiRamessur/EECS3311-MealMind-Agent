package com.mealmind.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RecommendationRequestTest {

    @Test
    void defaults() {
        RecommendationRequest request = RecommendationRequest.defaults();

        assertFalse(request.pantryFirst());
        assertNull(request.maxPrepMinutes());
        assertEquals(5, request.limit());
    }

    @Test
    void bounds() {
        assertEquals(1, new RecommendationRequest(true, 1, 1).maxPrepMinutes());
        assertEquals(10, new RecommendationRequest(true, null, 10).limit());
        assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(false, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(false, -3, 5));
        assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(false, null, 0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(false, null, 11));
        assertEquals("Limit must be between 1 and 10, got 11", e.getMessage());
    }
}
