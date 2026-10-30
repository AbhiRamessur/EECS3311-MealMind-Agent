package com.mealmind.tools;

import static com.mealmind.tools.ToolTestSupport.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.Unit;
import com.mealmind.shopping.Aisle;
import com.mealmind.shopping.ShoppingItem;
import com.mealmind.shopping.ShoppingList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** shopping_list reads the current list through a Supplier on every call. */
class ShoppingListToolTest {

    @Test
    void shoppingListItemsAislesNotesAndWarnings() {
        ShoppingList list = new ShoppingList(List.of(
                new ShoppingItem("Chicken breast", 300, Unit.GRAM, 4.5, Aisle.MEAT_SEAFOOD, null),
                new ShoppingItem("Egg", 3, Unit.COUNT, 1.2, Aisle.DAIRY_EGGS, "Needed in several incompatible units; listed separately")),
                List.of("Pantry is empty: all ingredients are listed as missing."));

        JsonNode result = ok(new ShoppingListTool(() -> list), "{}");

        assertEquals(2, result.get("count").asInt());
        assertEquals(5.7, result.get("estimatedTotal").asDouble());
        JsonNode chicken = result.get("items").get(0);
        assertEquals("Meat & Seafood", chicken.get("aisle").asText());
        assertEquals("g", chicken.get("unit").asText());
        assertTrue(chicken.get("note").isNull());
        assertEquals("Dairy & Eggs", result.get("items").get(1).get("aisle").asText());
        assertEquals("Needed in several incompatible units; listed separately", result.get("items").get(1).get("note").asText());
        assertEquals("Pantry is empty: all ingredients are listed as missing.", result.get("warnings").get(0).asText());
    }

    @Test
    void shoppingListSupplierIsReadOnEveryCall() {
        AtomicReference<ShoppingList> holder = new AtomicReference<>(ShoppingList.empty());
        ShoppingListTool tool = new ShoppingListTool(holder::get);
        assertEquals(0, ok(tool, "{}").get("count").asInt());

        holder.set(new ShoppingList(List.of(new ShoppingItem("Rice", 200, Unit.GRAM, 0.5, Aisle.PANTRY_STAPLES, null)), List.of()));

        assertEquals(1, ok(tool, "{}").get("count").asInt());
    }
}
