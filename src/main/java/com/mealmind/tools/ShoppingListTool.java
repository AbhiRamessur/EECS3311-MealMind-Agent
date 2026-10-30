package com.mealmind.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mealmind.shopping.ShoppingItem;
import com.mealmind.shopping.ShoppingList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@code shopping_list}: what still has to be bought for the current plan. The list is fetched from
 * the supplier on every call, so it always follows the current plan and pantry. Read-only.
 */
public final class ShoppingListTool implements Tool {

    private static final ToolSpec SPEC = new ToolSpec("shopping_list",
            "Shows the shopping list for the current plan: items still to buy, by aisle, with estimated costs.",
            List.of());

    private final Supplier<ShoppingList> list;

    /**
     * Creates the tool.
     *
     * @param list supplies the current shopping list on each call
     * @throws NullPointerException if {@code list} is null
     */
    public ShoppingListTool(Supplier<ShoppingList> list) {
        this.list = Objects.requireNonNull(list, "list");
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        ShoppingList current = list.get();
        ObjectNode result = ToolJson.MAPPER.createObjectNode();
        result.put("count", current.size());
        result.put("estimatedTotal", ToolJson.round2(current.getTotalEstimatedCost()));
        ArrayNode items = result.putArray("items");
        List<ShoppingItem> shoppingItems = current.getItems();
        for (ShoppingItem item : shoppingItems) {
            ObjectNode node = items.addObject();
            node.put("name", item.getName());
            node.put("quantity", ToolJson.round1(item.getQuantity()));
            node.put("unit", item.getUnit().getSymbol());
            node.put("aisle", item.getAisle().getDisplayName());
            node.put("estimatedCost", ToolJson.round2(item.getEstimatedCost()));
            if (item.getNote().isPresent()) {
                node.put("note", item.getNote().get());
            } else {
                node.putNull("note");
            }
        }
        ArrayNode warnings = result.putArray("warnings");
        current.getWarnings().forEach(warnings::add);
        return result;
    }
}
