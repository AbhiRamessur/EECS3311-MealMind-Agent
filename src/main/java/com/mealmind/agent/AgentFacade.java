package com.mealmind.agent;

import com.mealmind.llm.LlmService;
import com.mealmind.model.ObservableSubject;
import com.mealmind.model.RecipeBook;
import com.mealmind.profile.UserProfile;
import com.mealmind.tools.ToolManager;
import com.mealmind.validation.ConstraintValidator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The <strong>Facade</strong> for every AI feature: the single entry point the user interfaces use.
 *
 * <p>Roles: Facade = {@code AgentFacade}; Subsystem = {@link AgentLoop}, {@link RecipeGenerator},
 * {@link PromptBuilder}, {@link RecipeDraftParser}, {@link LlmService}, {@link ToolManager},
 * {@link ConstraintValidator} and {@link AgentMemory}; Clients = the CLI's command handler now and the
 * JavaFX controller later. Clients never touch the subsystem classes for AI features.
 *
 * <p>A generated recipe is held as the <em>pending</em> candidate (safe or flagged unsafe); it is
 * never saved automatically. The facade is also an {@link ObservableSubject}: observers are notified
 * once when a new candidate is stored and once when one is discarded. Only one request runs at a time.
 */
public final class AgentFacade extends ObservableSubject {

    private final LlmService llm;
    private final RecipeGenerator recipeGenerator;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile GeneratedRecipe pending;

    /**
     * Creates the facade and its subsystem.
     *
     * @param llm       the model access
     * @param tools     the read-only tools
     * @param memory    the session memory
     * @param validator the safety check
     * @param profile   the user's profile
     * @param recipes   the recipe book (read only)
     * @param limits    the agent budgets
     */
    public AgentFacade(LlmService llm, ToolManager tools, AgentMemory memory, ConstraintValidator validator,
                       UserProfile profile, RecipeBook recipes, AgentLimits limits) {
        this.llm = llm;
        AgentLoop loop = new AgentLoop(llm, tools, limits);
        this.recipeGenerator = new RecipeGenerator(loop, validator, profile, memory, tools, limits, recipes);
    }

    /**
     * Generates a recipe (F04) and stores it as the pending candidate, replacing any previous one.
     * An unsafe result is returned (and stored) flagged, not thrown: check {@link GeneratedRecipe#safe()}.
     *
     * @param prompt   what the user wants
     * @param activity receives agent activity events, or null
     * @return the candidate
     * @throws IllegalStateException if another request is already running
     */
    public GeneratedRecipe generateRecipe(String prompt, Consumer<AgentEvent> activity) {
        if (!busy.compareAndSet(false, true)) {
            throw new IllegalStateException("The agent is already working on a request");
        }
        try {
            GeneratedRecipe result = recipeGenerator.generate(prompt, activity);
            pending = result;
            notifyObservers("Pending recipe " + result.recipe().getId() + (result.safe() ? "" : " (flagged unsafe)"));
            return result;
        } finally {
            busy.set(false);
        }
    }

    /**
     * Returns the pending candidate.
     *
     * @return the last generated recipe, or empty if none or discarded
     */
    public Optional<GeneratedRecipe> pendingRecipe() {
        return Optional.ofNullable(pending);
    }

    /**
     * Discards the pending candidate.
     *
     * @return {@code true} if there was one
     */
    public synchronized boolean discardPendingRecipe() {
        GeneratedRecipe old = pending;
        if (old == null) {
            return false;
        }
        pending = null;
        notifyObservers("Discarded pending recipe " + old.recipe().getId());
        return true;
    }

    /**
     * Returns whether an AI provider is configured.
     *
     * @return {@code true} if AI calls can be made
     */
    public boolean isAiAvailable() {
        return llm.isAvailable();
    }

    /**
     * Returns why AI features are unavailable.
     *
     * @return the reason, or empty when available
     */
    public Optional<String> unavailableReason() {
        return llm.unavailableReason();
    }
}
