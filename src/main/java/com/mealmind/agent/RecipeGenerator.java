package com.mealmind.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.llm.ChatMessage;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.Role;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.profile.UserProfile;
import com.mealmind.tools.ToolManager;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.ValidationResult;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Generates one recipe from a request (F04): builds the prompt, runs the {@link AgentLoop}, parses
 * each draft with {@link RecipeDraftParser} and runs the deterministic {@link ConstraintValidator}
 * on it. A model's claim that a recipe is safe is never trusted: every draft is checked here.
 *
 * <p>The result is only a candidate; nothing is added to the recipe book. The agent memory is
 * updated (request and outcome) only when a recipe is produced.
 */
public final class RecipeGenerator {

    /** Longest accepted request, in characters. */
    public static final int MAX_PROMPT_LENGTH = 500;
    private static final int HISTORY_MESSAGES = 6;

    private final AgentLoop loop;
    private final ConstraintValidator validator;
    private final UserProfile profile;
    private final AgentMemory memory;
    private final ToolManager tools;
    private final AgentLimits limits;
    private final RecipeBook recipes;

    /** A checked draft; the run counters are added once the run is over. */
    private record Draft(Recipe recipe, ValidationResult validation) {
    }

    /**
     * Creates a generator.
     *
     * @param loop      the agent loop
     * @param validator the safety check
     * @param profile   the user's profile (for the prompt)
     * @param memory    conversation history and preference notes
     * @param tools     the tools (for the prompt)
     * @param limits    the agent budgets (for the prompt)
     * @param recipes   the recipe book, read only to choose an unused id
     * @throws NullPointerException if an argument is null
     */
    public RecipeGenerator(AgentLoop loop, ConstraintValidator validator, UserProfile profile, AgentMemory memory,
                           ToolManager tools, AgentLimits limits, RecipeBook recipes) {
        this.loop = Objects.requireNonNull(loop, "loop");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
    }

    /**
     * Generates a recipe.
     *
     * @param userPrompt what the user wants, at most {@value #MAX_PROMPT_LENGTH} characters
     * @param listener   receives agent activity events, or null
     * @return the candidate, safe or flagged unsafe
     * @throws IllegalArgumentException if the prompt is blank or too long
     * @throws AgentException           if no valid recipe was produced within the budgets
     * @throws LlmException             if a model call fails
     */
    public GeneratedRecipe generate(String userPrompt, Consumer<AgentEvent> listener) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("Describe the recipe you want");
        }
        String prompt = userPrompt.trim();
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException("The request is too long (at most " + MAX_PROMPT_LENGTH + " characters)");
        }
        String id = nextId();

        List<ChatMessage> seed = new ArrayList<>();
        seed.add(ChatMessage.system(PromptBuilder.recipeSystemPrompt(profile, memory.facts(), tools, limits)));
        seed.addAll(recentHistory());
        seed.add(ChatMessage.user(prompt));

        AgentRun<Draft> run = loop.run(seed, "recipe", answer -> check(answer, id, listener), listener);
        if (run.status() == RunStatus.FAILED) {
            throw new AgentException(run.message());
        }
        Draft draft = run.value().orElseThrow();
        GeneratedRecipe result = new GeneratedRecipe(draft.recipe(), draft.validation(), run.llmCalls(),
                run.toolSteps(), run.repairs(), run.safetyRetries(), run.usage());

        Recipe recipe = result.recipe();
        memory.addUserMessage(prompt);
        memory.addAssistantMessage(result.safe()
                ? "Proposed recipe: " + recipe.getName() + " (" + recipe.getId() + "), " + recipe.getServings() + " servings"
                : "Could not produce a recipe that is safe for this profile: " + recipe.getName());
        return result;
    }

    /** Parses and safety-checks one draft. */
    private Check<Draft> check(JsonNode answer, String id, Consumer<AgentEvent> listener) {
        RecipeDraftParser.Result parsed = RecipeDraftParser.parse(answer, id);
        if (parsed.recipe().isEmpty()) {
            return new Check.Repair<>("Your recipe has these problems: " + String.join("; ", parsed.problems())
                    + ". Fix them and resubmit the full recipe.");
        }
        Recipe recipe = parsed.recipe().get();
        ValidationResult validation = validator.validateAgainstBlacklists(recipe);
        List<Violation> violations = validation.getSafetyViolations();
        if (violations.isEmpty()) {
            AgentLoop.emit(listener, AgentEventType.VALIDATION, "Safety check passed");
            return new Check.Accepted<>(new Draft(recipe, validation));
        }
        AgentLoop.emit(listener, AgentEventType.VALIDATION, "Safety check: " + violations.size() + " issue(s)");
        String reasons = violations.stream().map(Violation::message).collect(Collectors.joining("; "));
        return new Check.Unsafe<>("The recipe violates the user's safety rules: " + reasons
                + ". Rewrite it without these ingredients and resubmit the full recipe.", new Draft(recipe, validation));
    }

    /** "AI" plus the lowest unused 3-digit number. */
    private String nextId() {
        for (int n = 1; n <= 999; n++) {
            String id = String.format("AI%03d", n);
            if (!recipes.contains(id)) {
                return id;
            }
        }
        throw new IllegalStateException("No free AI recipe id left");
    }

    /** The last few remembered messages, starting with a user message. */
    private List<ChatMessage> recentHistory() {
        List<ChatMessage> history = memory.history();
        List<ChatMessage> recent = new ArrayList<>(history.subList(Math.max(0, history.size() - HISTORY_MESSAGES), history.size()));
        if (!recent.isEmpty() && recent.get(0).role() == Role.ASSISTANT) {
            recent.remove(0);
        }
        return recent;
    }
}
