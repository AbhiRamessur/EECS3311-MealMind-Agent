package com.mealmind.tools;

import static com.mealmind.tools.ToolTestSupport.error;
import static com.mealmind.tools.ToolTestSupport.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PantryToolTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    private Pantry pantry;
    private PantryTool tool;

    @BeforeEach
    void setUp() {
        pantry = new Pantry();
        tool = new PantryTool(pantry, CLOCK);
    }

    private void stock() {
        pantry.addItem(new PantryItem("Milk", 1, Unit.LITER, TODAY.minusDays(1)));
        pantry.addItem(new PantryItem("Chicken Breast", 500, Unit.GRAM, TODAY.plusDays(2)));
        pantry.addItem(new PantryItem("Yogurt", 2, Unit.COUNT, TODAY.plusDays(3)));
        pantry.addItem(new PantryItem("Cheese", 200, Unit.GRAM, TODAY.plusDays(10)));
        pantry.addItem(new PantryItem("Rice", 1.25, Unit.KILOGRAM));
    }

    @Test
    void emptyPantry() {
        JsonNode result = ok(tool, "{}");

        assertEquals(0, result.get("count").asInt());
        assertEquals(0, result.get("items").size());
    }

    @Test
    void listsItemsWithUnitsAndExpiry() {
        stock();

        JsonNode result = ok(tool, "{}");

        assertEquals(5, result.get("count").asInt());
        assertEquals(List.of("Cheese", "Chicken Breast", "Milk", "Rice", "Yogurt"), result.get("items").findValuesAsText("name"));
        JsonNode rice = result.get("items").get(3);
        assertEquals(1.3, rice.get("quantity").asDouble()); // 1.25 rounded to 1 decimal
        assertEquals("kg", rice.get("unit").asText());
        assertTrue(rice.get("expiry").isNull());
        assertTrue(rice.get("daysUntilExpiry").isNull());
        JsonNode chicken = result.get("items").get(1);
        assertEquals("2026-10-28", chicken.get("expiry").asText());
        assertEquals(2, chicken.get("daysUntilExpiry").asInt());
    }

    @Test
    void ingredientLookupIgnoresCaseAndSpacing() {
        stock();

        JsonNode found = ok(tool, "{\"ingredient\": \"  CHICKEN   breast \"}");

        assertEquals("CHICKEN   breast", found.get("ingredient").asText()); // as given, trimmed by validation
        assertTrue(found.get("inPantry").asBoolean());
        assertEquals(500, found.get("quantity").asDouble());
        assertEquals("g", found.get("unit").asText());
        assertEquals(2, found.get("daysUntilExpiry").asInt());
    }

    @Test
    void ingredientNotFound() {
        JsonNode result = ok(tool, "{\"ingredient\": \"tofu\"}");

        assertEquals("tofu", result.get("ingredient").asText());
        assertFalse(result.get("inPantry").asBoolean());
        assertFalse(result.has("quantity"));
    }

    @Test
    void expiringIncludesExpiredAndBoundaryItemsInPantryOrder() {
        stock();

        JsonNode result = ok(tool, "{\"expiringWithinDays\": 3}");

        assertEquals(3, result.get("withinDays").asInt());
        assertEquals(3, result.get("count").asInt());
        assertEquals(List.of("Milk", "Chicken Breast", "Yogurt"), result.get("items").findValuesAsText("name"));
        assertTrue(result.get("items").get(0).get("expired").asBoolean());
        assertFalse(result.get("items").get(2).get("expired").asBoolean());
    }

    @Test
    void invalidArgumentCombinationsAndRanges() {
        assertEquals("Provide either ingredient or expiringWithinDays, not both",
                error(tool, "{\"ingredient\": \"rice\", \"expiringWithinDays\": 3}"));
        assertEquals("expiringWithinDays must be between 0 and 60, got -1", error(tool, "{\"expiringWithinDays\": -1}"));
        assertEquals("expiringWithinDays must be between 0 and 60, got 61", error(tool, "{\"expiringWithinDays\": 61}"));
    }

    @Test
    void todayComesFromTheInjectedClock() {
        stock();
        Clock later = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

        JsonNode now = ok(tool, "{\"expiringWithinDays\": 0}");
        JsonNode afterAWeek = ok(new PantryTool(pantry, later), "{\"expiringWithinDays\": 0}");

        assertEquals(1, now.get("count").asInt());
        assertEquals(3, afterAWeek.get("count").asInt());
    }

    @Test
    void doesNotModifyThePantry() {
        stock();
        List<PantryItem> before = pantry.getItems();

        ok(tool, "{}");
        ok(tool, "{\"ingredient\": \"rice\"}");
        ok(tool, "{\"expiringWithinDays\": 30}");

        assertEquals(before, pantry.getItems());
    }
}
