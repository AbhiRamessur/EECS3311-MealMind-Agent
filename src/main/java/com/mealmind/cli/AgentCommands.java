package com.mealmind.cli;

import com.mealmind.agent.AgentFacade;
import com.mealmind.agent.AgentMemory;
import com.mealmind.agent.GeneratedRecipe;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Recipe;
import com.mealmind.validation.Violation;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The AI agent commands: {@code recipe generate/pending/discard} (F04) and {@code memory}. They only
 * use the {@link AgentFacade} and {@link AgentMemory}; a generated recipe is a pending candidate and
 * is never saved here.
 */
final class AgentCommands {

    static final String GENERATE_USAGE = "recipe generate \"<what you want to eat>\"";
    static final String MEMORY_USAGE = "memory show | memory remember \"<fact>\" | memory forget \"<fact>\" | memory clear";

    private final AgentFacade agent;
    private final AgentMemory memory;
    private final PrintStream out;

    AgentCommands(AgentFacade agent, AgentMemory memory, PrintStream out) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.out = Objects.requireNonNull(out, "out");
    }

    // ---- recipes ----

    /** {@code recipe generate <words...>}: activity is printed live as "[agent] ..." lines. */
    void generate(List<String> words) {
        if (words.isEmpty()) {
            throw new UsageException(GENERATE_USAGE);
        }
        GeneratedRecipe result = agent.generateRecipe(String.join(" ", words),
                event -> out.println("[agent] " + event.message()));
        printPreview(result);
        out.println("Agent: " + result.llmCalls() + " model calls, " + result.toolSteps() + " tool calls, "
                + result.repairs() + " repairs, " + result.safetyRetries() + " safety retries; tokens: "
                + result.usage().promptTokens() + " prompt + " + result.usage().completionTokens() + " completion");
        printSafety(result);
    }

    /** {@code recipe pending}. */
    void pending() {
        Optional<GeneratedRecipe> pending = agent.pendingRecipe();
        if (pending.isEmpty()) {
            out.println("No pending recipe.");
            return;
        }
        printPreview(pending.get());
        printSafety(pending.get());
    }

    /** {@code recipe discard}. */
    void discard() {
        Optional<GeneratedRecipe> pending = agent.pendingRecipe();
        if (pending.isPresent() && agent.discardPendingRecipe()) {
            out.println("Discarded pending recipe " + pending.get().recipe().getId() + ".");
        } else {
            out.println("No pending recipe.");
        }
    }

    private void printPreview(GeneratedRecipe generated) {
        Recipe recipe = generated.recipe();
        out.println("Generated recipe " + recipe.getId() + ": " + recipe.getName());
        out.println("Servings: " + recipe.getServings() + " | Prep: " + recipe.getPrepMinutes() + " min");
        out.println("Per serving: " + CliFormat.nutrition(recipe, recipe.getServings()));
        out.println("Ingredients:");
        for (Ingredient ingredient : recipe.getIngredients()) {
            out.println("  - " + CliFormat.number(ingredient.getQuantity(), ingredient.getUnit()) + " "
                    + ingredient.getUnit().getSymbol() + " " + ingredient.getName());
        }
        out.println("Steps:");
        List<String> steps = recipe.getInstructions();
        for (int i = 0; i < steps.size(); i++) {
            out.println("  " + (i + 1) + ". " + steps.get(i));
        }
        out.println("Nutrition and cost values are AI estimates.");
    }

    private void printSafety(GeneratedRecipe generated) {
        String id = generated.recipe().getId();
        if (generated.safe()) {
            out.println("Safety check: passed");
        } else {
            out.println("SAFETY WARNING: this recipe could not be made safe for your profile after "
                    + generated.safetyRetries() + " attempts. It must not be used:");
            for (Violation violation : generated.validation().getSafetyViolations()) {
                out.println("    " + violation.message());
            }
            out.println("Pending recipe " + id + " is flagged unsafe.");
        }
        out.println("Pending recipe: " + id + " (not saved).");
    }

    // ---- memory ----

    /** {@code memory <subcommand> ...}. */
    void memory(List<String> args) {
        if (args.isEmpty()) {
            throw new UsageException(MEMORY_USAGE);
        }
        String rest = String.join(" ", args.subList(1, args.size()));
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "show" -> showMemory();
            case "remember" -> out.println(memory.remember(rest) ? "Remembered: " + rest.trim().replaceAll("\\s+", " ")
                    : "Already remembered.");
            case "forget" -> out.println(memory.forget(rest) ? "Forgot: " + rest.trim().replaceAll("\\s+", " ") : "Not found.");
            case "clear" -> {
                memory.clearAll();
                out.println("Cleared remembered preferences and conversation.");
            }
            default -> throw new UsageException(MEMORY_USAGE);
        }
    }

    private void showMemory() {
        List<String> facts = memory.facts();
        if (facts.isEmpty()) {
            out.println("No remembered preferences.");
        }
        for (int i = 0; i < facts.size(); i++) {
            out.println((i + 1) + ". " + facts.get(i));
        }
        out.println("Conversation messages: " + memory.size());
    }
}
