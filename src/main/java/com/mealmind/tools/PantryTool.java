package com.mealmind.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code pantry_query}: what is in the pantry. With no arguments it lists every item; with
 * {@code ingredient} it looks one item up; with {@code expiringWithinDays} it lists items expiring
 * soon, including already-expired ones. "Today" comes from the injected clock. Read-only.
 */
public final class PantryTool implements Tool {

    private static final int MAX_DAYS = 60;

    private static final ToolSpec SPEC = new ToolSpec("pantry_query",
            "Lists pantry stock. No arguments: every item. 'ingredient': whether that item is in stock and how much. "
                    + "'expiringWithinDays': items expiring within that many days, including expired ones.",
            List.of(ToolParam.optional("ingredient", ParamType.STRING, "Name of one ingredient to look up"),
                    ToolParam.optional("expiringWithinDays", ParamType.INTEGER,
                            "List items expiring within this many days (0 to " + MAX_DAYS + ")")));

    private final Pantry pantry;
    private final Clock clock;

    /**
     * Creates the tool.
     *
     * @param pantry the pantry to read
     * @param clock  the source of "today"
     * @throws NullPointerException if an argument is null
     */
    public PantryTool(Pantry pantry, Clock clock) {
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        boolean byIngredient = arguments.has("ingredient");
        boolean byExpiry = arguments.has("expiringWithinDays");
        if (byIngredient && byExpiry) {
            throw new IllegalArgumentException("Provide either ingredient or expiringWithinDays, not both");
        }
        LocalDate today = LocalDate.now(clock);
        if (byIngredient) {
            return lookUp(arguments.get("ingredient").asText(), today);
        }
        if (byExpiry) {
            return expiring(arguments.get("expiringWithinDays").asInt(), today);
        }
        ObjectNode result = ToolJson.MAPPER.createObjectNode();
        List<PantryItem> items = pantry.getItems();
        result.put("count", items.size());
        ArrayNode array = result.putArray("items");
        items.forEach(item -> array.add(itemNode(item, today)));
        return result;
    }

    private JsonNode lookUp(String ingredient, LocalDate today) {
        ObjectNode result = ToolJson.MAPPER.createObjectNode();
        result.put("ingredient", ingredient);
        Optional<PantryItem> item = pantry.getItem(ingredient);
        result.put("inPantry", item.isPresent());
        item.ifPresent(found -> {
            ObjectNode details = itemNode(found, today);
            details.remove("name");
            result.setAll(details);
        });
        return result;
    }

    private JsonNode expiring(int days, LocalDate today) {
        if (days < 0 || days > MAX_DAYS) {
            throw new IllegalArgumentException("expiringWithinDays must be between 0 and " + MAX_DAYS + ", got " + days);
        }
        List<PantryItem> items = pantry.getExpiringItems(today, days);
        ObjectNode result = ToolJson.MAPPER.createObjectNode();
        result.put("withinDays", days);
        result.put("count", items.size());
        ArrayNode array = result.putArray("items");
        for (PantryItem item : items) {
            array.add(itemNode(item, today).put("expired", item.isExpired(today)));
        }
        return result;
    }

    /** {@code {"name", "quantity", "unit", "expiry", "daysUntilExpiry"}}, with nulls when there is no expiry. */
    private static ObjectNode itemNode(PantryItem item, LocalDate today) {
        ObjectNode node = ToolJson.MAPPER.createObjectNode();
        node.put("name", item.getName());
        node.put("quantity", ToolJson.round1(item.getQuantity()));
        node.put("unit", item.getUnit().getSymbol());
        if (item.getExpiry().isPresent()) {
            node.put("expiry", item.getExpiry().get().toString());
            node.put("daysUntilExpiry", (int) item.daysUntilExpiry(today));
        } else {
            node.putNull("expiry");
            node.putNull("daysUntilExpiry");
        }
        return node;
    }
}
