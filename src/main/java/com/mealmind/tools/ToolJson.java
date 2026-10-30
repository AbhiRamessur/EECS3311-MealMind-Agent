package com.mealmind.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mealmind.model.MealComponent;

/**
 * JSON helpers shared by the tools: one mapper and consistent number rounding.
 */
final class ToolJson {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolJson() {
    }

    /** Rounds to 1 decimal (quantities, calories, macros). */
    static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** Rounds to 2 decimals (money). */
    static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** {@code {"calories", "proteinG", "carbsG", "fatG", "cost"}} for a component divided by {@code servings}. */
    static ObjectNode nutrition(MealComponent component, int servings) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("calories", round1(component.getCalories() / servings));
        node.put("proteinG", round1(component.getProteinG() / servings));
        node.put("carbsG", round1(component.getCarbsG() / servings));
        node.put("fatG", round1(component.getFatG() / servings));
        node.put("cost", round2(component.getCost() / servings));
        return node;
    }
}
