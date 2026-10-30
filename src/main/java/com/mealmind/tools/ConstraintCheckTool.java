package com.mealmind.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code check_safety}: runs the deterministic {@link ConstraintValidator} on recipes by id, exposing
 * the Stage 1 ConstraintValidator to the agent as a tool. Unlike {@code recipe_search}, it does reveal
 * the violation messages, because the model may need the reason to correct a proposal. Read-only.
 */
public final class ConstraintCheckTool implements Tool {

    private static final int MAX_IDS = 10;

    private static final ToolSpec SPEC = new ToolSpec("check_safety",
            "Checks recipes against the user's allergens and diet and explains any violation.",
            List.of(ToolParam.required("recipeIds", ParamType.STRING_ARRAY, "1 to " + MAX_IDS + " recipe ids to check")));

    private final RecipeBook recipeBook;
    private final ConstraintValidator validator;

    /**
     * Creates the tool.
     *
     * @param recipeBook where recipes are looked up
     * @param validator  the safety check against the current profile
     * @throws NullPointerException if an argument is null
     */
    public ConstraintCheckTool(RecipeBook recipeBook, ConstraintValidator validator) {
        this.recipeBook = Objects.requireNonNull(recipeBook, "recipeBook");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        JsonNode ids = arguments.get("recipeIds");
        if (ids.isEmpty() || ids.size() > MAX_IDS) {
            throw new IllegalArgumentException("recipeIds must contain 1 to " + MAX_IDS + " ids, got " + ids.size());
        }
        ObjectNode result = ToolJson.MAPPER.createObjectNode();
        ArrayNode results = result.putArray("results");
        for (JsonNode idNode : ids) {
            String id = idNode.asText();
            Optional<Recipe> recipe = recipeBook.get(id);
            List<Violation> problems = recipe.map(r -> validator.validateAgainstBlacklists(r).getSafetyViolations())
                    .orElse(List.of());
            ObjectNode entry = results.addObject();
            entry.put("id", id);
            entry.put("found", recipe.isPresent());
            entry.put("safe", recipe.isPresent() && problems.isEmpty());
            ArrayNode violations = entry.putArray("violations");
            problems.forEach(violation -> violations.add(violation.message()));
        }
        return result;
    }
}
