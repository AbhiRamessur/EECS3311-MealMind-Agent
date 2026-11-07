package com.mealmind.agent;

import com.mealmind.command.CommandHistory;
import com.mealmind.command.SaveRecipeCommand;
import com.mealmind.llm.LlmErrorKind;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.LlmService;
import com.mealmind.model.ObservableSubject;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.nutrition.NutritionSummary;
import com.mealmind.profile.UserProfile;
import com.mealmind.recommend.RecommendationResult;
import com.mealmind.tools.ToolManager;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The <strong>Facade</strong> for every AI feature: the single entry point the user interfaces use.
 *
 * <p>Roles: Facade = {@code AgentFacade}; Subsystem = {@link AgentLoop}, {@link RecipeGenerator},
 * {@link NutritionCommentator}, {@link RecommendationRanker}, {@link PromptBuilder}, {@link RecipeDraftParser}, {@link LlmService}, {@link ToolManager},
 * {@link ConstraintValidator} and {@link AgentMemory}; Clients = the CLI's command handler now and the
 * JavaFX controller later. Clients never touch the subsystem classes for AI features.
 *
 * <p>A generated recipe is held as the <em>pending</em> candidate (safe or flagged unsafe); it is
 * never saved automatically: only {@link #savePendingRecipe} saves it, after re-checking its safety.
 * The facade is also an {@link ObservableSubject}: observers are notified once when a candidate is
 * stored, saved or discarded. Only one request runs at a time, whichever AI feature it is for.
 */
public final class AgentFacade extends ObservableSubject {

    private final LlmService llm;
    private final ConstraintValidator validator;
    private final RecipeBook recipes;
    private final RecipeGenerator recipeGenerator;
    private final UserProfile profile;
    private final NutritionCommentator nutritionCommentator;
    private final RecommendationRanker recommendationRanker;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile GeneratedRecipe pending;

    /**
     * Creates the facade and its subsystem, without rating history in the prompts.
     *
     * @param llm       the model access
     * @param tools     the read-only tools
     * @param memory    the session memory
     * @param validator the safety check
     * @param profile   the user's profile
     * @param recipes   the recipe book (read for ids; written only by {@link #savePendingRecipe})
     * @param limits    the agent budgets
     */
    public AgentFacade(LlmService llm, ToolManager tools, AgentMemory memory, ConstraintValidator validator,
                       UserProfile profile, RecipeBook recipes, AgentLimits limits) {
        this(llm, tools, memory, validator, profile, recipes, limits, List::of);
    }

    /**
     * Creates the facade and its subsystem; prompts include notes derived from the user's ratings (F12).
     *
     * @param llm         the model access
     * @param tools       the read-only tools
     * @param memory      the session memory
     * @param validator   the safety check
     * @param profile     the user's profile
     * @param recipes     the recipe book (read for ids; written only by {@link #savePendingRecipe})
     * @param limits      the agent budgets
     * @param ratingNotes supplies the rating-derived prompt notes, once per generation
     */
    public AgentFacade(LlmService llm, ToolManager tools, AgentMemory memory, ConstraintValidator validator,
                       UserProfile profile, RecipeBook recipes, AgentLimits limits, Supplier<List<String>> ratingNotes) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.profile = Objects.requireNonNull(profile, "profile");
        AgentLoop loop = new AgentLoop(llm, tools, limits);
        this.recipeGenerator = new RecipeGenerator(loop, validator, profile, memory, tools, limits, recipes, ratingNotes);
        this.nutritionCommentator = new NutritionCommentator(llm);
        this.recommendationRanker = new RecommendationRanker(llm);
    }

    /**
     * Generates a recipe (F04) and stores it as the pending candidate, replacing any previous one.
     * An unsafe result is returned (and stored) flagged, not thrown: check {@link GeneratedRecipe#safe()}.
     *
     * @param prompt   what the user wants
     * @param activity receives agent activity events, or null
     * @return the candidate
     * @throws IllegalStateException if another request is already running
     * @throws LlmException          {@link LlmErrorKind#UNAVAILABLE} at once, before any agent activity,
     *                               when AI is not configured
     */
    public GeneratedRecipe generateRecipe(String prompt, Consumer<AgentEvent> activity) {
        GeneratedRecipe result = exclusive(() -> recipeGenerator.generate(prompt, activity));
        pending = result;
        notifyObservers("Pending recipe " + result.recipe().getId() + (result.safe() ? "" : " (flagged unsafe)"));
        return result;
    }

    /**
     * Writes AI commentary on computed nutrition figures (F09). The model only interprets the figures;
     * it never changes them. Observers are not notified and the pending recipe is not touched.
     *
     * @param summary  the computed summary
     * @param activity receives agent activity events, or null
     * @return the commentary
     * @throws IllegalStateException if another request is already running
     * @throws LlmException          {@link LlmErrorKind#UNAVAILABLE} at once when AI is not configured
     * @throws AgentException        if no valid commentary was produced
     */
    public String explainNutrition(NutritionSummary summary, Consumer<AgentEvent> activity) {
        Objects.requireNonNull(summary, "summary");
        return exclusive(() -> nutritionCommentator.comment(summary, profile, activity));
    }

    /**
     * Re-ranks deterministic recommendations with AI (F03). The model may only reorder the given items
     * and rewrite their reasons. Observers are not notified and the pending recipe is not touched.
     *
     * @param result   the deterministic recommendations
     * @param activity receives agent activity events, or null
     * @return the reordered result
     * @throws IllegalStateException if another request is already running
     * @throws LlmException          {@link LlmErrorKind#UNAVAILABLE} at once when AI is not configured
     * @throws AgentException        if no valid ranking was produced
     */
    public RecommendationResult rerankRecommendations(RecommendationResult result, Consumer<AgentEvent> activity) {
        Objects.requireNonNull(result, "result");
        return exclusive(() -> recommendationRanker.rerank(result, profile, activity));
    }

    /**
     * Runs one AI job: checks availability first (before any agent activity), then holds the busy guard
     * so two jobs never overlap.
     */
    private <T> T exclusive(Supplier<T> job) {
        if (!llm.isAvailable()) {
            throw new LlmException(LlmErrorKind.UNAVAILABLE,
                    "AI features are disabled: " + llm.unavailableReason().orElse("not configured"), null);
        }
        if (!busy.compareAndSet(false, true)) {
            throw new IllegalStateException("The agent is already working on a request");
        }
        try {
            return job.get();
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
     * Saves the pending candidate to the recipe book through an undoable {@link SaveRecipeCommand},
     * closing the F04 preview/save flow. The recipe is checked <strong>again</strong> against the
     * current profile first, so a candidate that is (or has become) unsafe is never saved. On any
     * failure the candidate stays pending and observers are not notified.
     *
     * @param history the command history to run the save through
     * @return the saved recipe
     * @throws NoSuchElementException if there is no pending recipe
     * @throws IllegalStateException  if the recipe is unsafe or its id is already taken
     */
    public synchronized Recipe savePendingRecipe(CommandHistory history) {
        Objects.requireNonNull(history, "history");
        GeneratedRecipe candidate = pending;
        if (candidate == null) {
            throw new NoSuchElementException("No pending recipe");
        }
        Recipe recipe = candidate.recipe();
        List<Violation> violations = validator.validateAgainstBlacklists(recipe).getSafetyViolations();
        if (!violations.isEmpty()) {
            throw new IllegalStateException("The recipe is flagged unsafe and cannot be saved: "
                    + violations.stream().map(Violation::message).collect(Collectors.joining("; ")));
        }
        if (recipes.contains(recipe.getId())) {
            throw new IllegalStateException("A recipe with id " + recipe.getId()
                    + " already exists; discard the pending recipe and generate again");
        }
        history.pushAndExecute(new SaveRecipeCommand(recipes, recipe));
        pending = null;
        notifyObservers("Saved recipe " + recipe.getId());
        return recipe;
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
