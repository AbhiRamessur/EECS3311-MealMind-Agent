package com.mealmind.app;

import com.mealmind.agent.AgentFacade;
import com.mealmind.agent.AgentLimits;
import com.mealmind.agent.AgentMemory;
import com.mealmind.agent.PreferenceNotes;
import com.mealmind.command.CommandHistory;
import com.mealmind.llm.LlmService;
import com.mealmind.model.MealPlan;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.persistence.PantryRepository;
import com.mealmind.persistence.PreferencesRepository;
import com.mealmind.persistence.ProfileRepository;
import com.mealmind.persistence.RecipeRepository;
import com.mealmind.persistence.RepositoryException;
import com.mealmind.planning.RulePlanner;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.UserProfile;
import com.mealmind.shopping.ShoppingListGenerator;
import com.mealmind.tools.ConstraintCheckTool;
import com.mealmind.tools.PantryTool;
import com.mealmind.tools.PlanTool;
import com.mealmind.tools.RecipeSearchTool;
import com.mealmind.tools.ShoppingListTool;
import com.mealmind.tools.ToolManager;
import com.mealmind.validation.ConstraintValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Holds the shared model objects for one MealMind session: the pantry, recipe book, current meal
 * plan, command history, shopping-list generator, user profile, constraint validator, AI service,
 * the agent's read-only tools, its session memory, the recipe ratings and favourites, and the
 * {@link AgentFacade} for AI features, plus the clock and data directory. The AI service starts
 * unavailable; {@code Main} configures it. The CLI (and later the GUI) work through this one object.
 *
 * <p><strong>Persistence:</strong> {@code <dataDir>} holds {@code pantry.json}, {@code profile.json},
 * {@code user-recipes.json} (recipes the user saved; bundled sample recipes are never written there)
 * and {@code ratings.json}. Everything is loaded first; only then are autosave observers attached, so
 * loading never triggers a save. A failed save is reported through the warning sink and never stops
 * the session; a corrupt file stops startup with a {@link RepositoryException} and is never
 * overwritten. The meal plan is kept in memory only.
 *
 * <p>Known limitation: scaling a <em>saved</em> recipe replaces it in the book and is therefore
 * persisted, while bundled recipes always reload at their original servings.
 */
public final class ApplicationContext {

    /** Name of the plan created at startup and by {@link #newPlan}. */
    public static final String PLAN_NAME = "Weekly Plan";

    private static final int DEFAULT_PLAN_DAYS = 7;
    private static final String PANTRY_FILE = "pantry.json";
    private static final String PROFILE_FILE = "profile.json";
    private static final String USER_RECIPES_FILE = "user-recipes.json";
    private static final String RATINGS_FILE = "ratings.json";

    private final Clock clock;
    private final Path dataDir;
    private final Pantry pantry;
    private final RecipeBook recipeBook;
    private final CommandHistory history;
    private final UserProfile profile;
    private final ConstraintValidator validator;
    private final LlmService llm = new LlmService();
    private final AgentMemory memory = new AgentMemory();
    private final ToolManager tools = new ToolManager();
    private final AgentFacade agent;
    private final RecipePreferences preferences;
    private final Set<String> bundledIds;
    private final RulePlanner planner;
    private MealPlan plan;
    private ShoppingListGenerator generator;
    private Consumer<String> warningSink = System.err::println;

    private ApplicationContext(Clock clock, Path dataDir, Pantry pantry, RecipeBook recipeBook, UserProfile profile,
                               Set<String> bundledIds, Consumer<String> warningSink) {
        this.bundledIds = Set.copyOf(bundledIds);
        this.warningSink = warningSink;
        // Created first so its pruning observer runs before the recipe-book autosave observer.
        this.preferences = new RecipePreferences(recipeBook);
        this.clock = clock;
        this.dataDir = dataDir;
        this.pantry = pantry;
        this.recipeBook = recipeBook;
        this.profile = profile;
        this.validator = new ConstraintValidator(profile);
        this.history = new CommandHistory();
        this.plan = new MealPlan(PLAN_NAME, DEFAULT_PLAN_DAYS);
        this.generator = new ShoppingListGenerator(plan, pantry);
        registerTools();
        this.agent = new AgentFacade(llm, tools, memory, validator, profile, recipeBook, AgentLimits.defaults(),
                () -> PreferenceNotes.build(preferences, recipeBook));
        this.planner = new RulePlanner(recipeBook, pantry, validator, preferences, profile);
    }

    /**
     * Registers the five read-only agent tools. The plan and shopping-list tools read the CURRENT plan
     * and generator through suppliers, so they keep working after {@link #newPlan} replaces them.
     */
    private void registerTools() {
        tools.register(new PantryTool(pantry, clock));
        tools.register(new RecipeSearchTool(recipeBook, pantry, validator, preferences));
        tools.register(new ConstraintCheckTool(recipeBook, validator));
        tools.register(new PlanTool(() -> plan));
        tools.register(new ShoppingListTool(() -> generator.getCurrent()));
    }

    /**
     * Creates a session, reporting warnings to standard error. See
     * {@link #create(Path, Clock, Consumer)}.
     *
     * @param dataDir where user data is stored
     * @param clock   the clock used for "today" (expiry checks)
     * @return the new context
     * @throws RepositoryException if the data directory cannot be created or saved data is invalid
     * @throws NullPointerException if an argument is null
     */
    public static ApplicationContext create(Path dataDir, Clock clock) {
        return create(dataDir, clock, System.err::println);
    }

    /**
     * Creates a session: makes {@code dataDir} if needed; loads the bundled sample recipes, then the
     * user's saved recipes, the pantry, the profile and the ratings (missing files mean none); creates
     * the plan, history, generator, validator, tools and agent; and only then starts autosaving, so
     * loading never triggers a save. Ratings for recipes that no longer exist are dropped with a warning.
     *
     * @param dataDir     where user data is stored
     * @param clock       the clock used for "today" (expiry checks)
     * @param warningSink receives non-fatal warnings (dropped ratings, failed saves)
     * @return the new context
     * @throws RepositoryException if the data directory cannot be created or saved data is invalid,
     *                             including a saved recipe that reuses a bundled recipe id
     * @throws NullPointerException if an argument is null
     */
    public static ApplicationContext create(Path dataDir, Clock clock, Consumer<String> warningSink) {
        Objects.requireNonNull(dataDir, "dataDir");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(warningSink, "warningSink");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RepositoryException("Could not create data directory " + dataDir + ": " + e.getMessage(), e);
        }

        RecipeBook recipeBook = new RecipeBook();
        RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES).forEach(recipeBook::put);
        Set<String> bundledIds = Set.copyOf(recipeBook.getAll().stream().map(Recipe::getId).toList());
        loadUserRecipes(dataDir.resolve(USER_RECIPES_FILE), recipeBook, bundledIds);
        Pantry pantry = new Pantry();
        for (PantryItem item : PantryRepository.load(dataDir.resolve(PANTRY_FILE))) {
            pantry.replaceItem(item);
        }
        UserProfile profile = new UserProfile();
        profile.applyData(ProfileRepository.load(dataDir.resolve(PROFILE_FILE)));

        ApplicationContext context = new ApplicationContext(clock, dataDir, pantry, recipeBook, profile, bundledIds,
                warningSink);
        List<String> dropped = context.preferences.applyData(PreferencesRepository.load(context.ratingsFile()));
        if (!dropped.isEmpty()) {
            warningSink.accept("Ignored ratings for unknown recipes: " + String.join(", ", dropped));
        }

        pantry.addObserver(event -> context.saveSafely("pantry",
                () -> PantryRepository.save(context.pantryFile(), pantry.getItems())));
        profile.addObserver(event -> context.saveSafely("profile",
                () -> ProfileRepository.save(context.profileFile(), profile.toData())));
        recipeBook.addObserver(event -> context.saveSafely("recipe", context::saveUserRecipes));
        context.preferences.addObserver(event -> context.saveSafely("ratings",
                () -> PreferencesRepository.save(context.ratingsFile(), context.preferences.toData())));
        return context;
    }

    /** Adds the saved user recipes; one that reuses a bundled id is an error. */
    private static void loadUserRecipes(Path file, RecipeBook recipeBook, Set<String> bundledIds) {
        if (!Files.exists(file)) {
            return;
        }
        for (Recipe recipe : RecipeRepository.loadFromFile(file)) {
            if (bundledIds.contains(recipe.getId())) {
                throw new RepositoryException(file + ": recipe " + recipe.getId()
                        + " uses the id of a bundled sample recipe");
            }
            recipeBook.put(recipe);
        }
    }

    /** Writes the non-bundled recipes, or deletes the file when there are none (an empty list is not valid). */
    private void saveUserRecipes() {
        List<Recipe> userRecipes = recipeBook.getAll().stream().filter(r -> !bundledIds.contains(r.getId())).toList();
        Path file = userRecipesFile();
        if (!userRecipes.isEmpty()) {
            RecipeRepository.save(file, userRecipes);
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RepositoryException("Could not delete " + file + ": " + e.getMessage(), e);
        }
    }

    /** Runs an autosave; a failure is reported through the warning sink instead of being thrown. */
    private void saveSafely(String what, Runnable save) {
        try {
            save.run();
        } catch (RepositoryException e) {
            warningSink.accept("Warning: " + what + " changes were not saved. " + e.getMessage());
        }
    }

    /**
     * Replaces the current plan with a new empty one. The old generator is detached and a new one
     * created for the new plan. The command history is <strong>cleared</strong>, because commands
     * such as {@code ReplaceMealCommand} hold a reference to the old plan; undoing them afterwards
     * would silently change a plan that is no longer shown.
     *
     * @param days the number of days, from 1 to 7
     * @return the new plan
     * @throws IllegalArgumentException if {@code days} is out of range; nothing is changed then
     */
    public MealPlan newPlan(int days) {
        MealPlan replacement = new MealPlan(PLAN_NAME, days);
        generator.detach();
        plan = replacement;
        generator = new ShoppingListGenerator(replacement, pantry);
        history.clear();
        return replacement;
    }

    /**
     * Returns the data directory from the environment: {@code MEALMIND_DATA_DIR} if set and not
     * blank, otherwise {@code data} in the current directory.
     *
     * @param env the environment variables
     * @return the data directory
     */
    public static Path defaultDataDir(Map<String, String> env) {
        String configured = env.get("MEALMIND_DATA_DIR");
        return (configured == null || configured.isBlank()) ? Path.of("data") : Path.of(configured.trim());
    }

    /**
     * Sets where non-fatal warnings (such as a failed autosave) are reported. Defaults to standard error.
     *
     * @param warningSink the receiver of warning messages
     * @throws NullPointerException if {@code warningSink} is null
     */
    public void setWarningSink(Consumer<String> warningSink) {
        this.warningSink = Objects.requireNonNull(warningSink, "warningSink");
    }

    /**
     * Returns the clock used for "today".
     *
     * @return the clock
     */
    public Clock clock() {
        return clock;
    }

    /**
     * Returns the data directory.
     *
     * @return the directory
     */
    public Path dataDir() {
        return dataDir;
    }

    /**
     * Returns the file the pantry is saved to.
     *
     * @return {@code <dataDir>/pantry.json}
     */
    public Path pantryFile() {
        return dataDir.resolve(PANTRY_FILE);
    }

    /**
     * Returns the file the saved user recipes are written to.
     *
     * @return {@code <dataDir>/user-recipes.json}
     */
    public Path userRecipesFile() {
        return dataDir.resolve(USER_RECIPES_FILE);
    }

    /**
     * Returns the file ratings and favourites are written to.
     *
     * @return {@code <dataDir>/ratings.json}
     */
    public Path ratingsFile() {
        return dataDir.resolve(RATINGS_FILE);
    }

    /**
     * Returns the recipe ratings and favourites.
     *
     * @return the preferences
     */
    public RecipePreferences preferences() {
        return preferences;
    }

    /**
     * Returns whether a recipe is one of the bundled sample recipes (which cannot be removed).
     *
     * @param id the recipe id
     * @return {@code true} for a bundled recipe
     */
    public boolean isBundledRecipe(String id) {
        return bundledIds.contains(Objects.requireNonNull(id, "id").trim());
    }

    /**
     * Returns the file the profile is saved to.
     *
     * @return {@code <dataDir>/profile.json}
     */
    public Path profileFile() {
        return dataDir.resolve(PROFILE_FILE);
    }

    /**
     * Returns the user's dietary profile.
     *
     * @return the profile
     */
    public UserProfile profile() {
        return profile;
    }

    /**
     * Returns the validator that checks meals and plans against {@link #profile()}.
     *
     * @return the validator
     */
    public ConstraintValidator validator() {
        return validator;
    }

    /**
     * Returns the AI service (the LLM Strategy context). It is always present but starts unavailable
     * until a client is configured.
     *
     * @return the service
     */
    public LlmService llm() {
        return llm;
    }

    /**
     * Returns the agent's tools (pantry_query, recipe_search, check_safety, plan_show, shopping_list).
     *
     * @return the tool manager
     */
    public ToolManager tools() {
        return tools;
    }

    /**
     * Returns the facade for AI features (recipe generation and the pending candidate).
     *
     * @return the agent facade
     */
    public AgentFacade agent() {
        return agent;
    }

    /**
     * Returns the agent's session memory (conversation window and facts), empty at startup.
     *
     * @return the memory
     */
    public AgentMemory memory() {
        return memory;
    }

    /**
     * Returns the pantry.
     *
     * @return the pantry
     */
    public Pantry pantry() {
        return pantry;
    }

    /**
     * Returns the recipe book.
     *
     * @return the recipe book
     */
    public RecipeBook recipeBook() {
        return recipeBook;
    }

    /**
     * Returns the current meal plan; it changes when {@link #newPlan} is called.
     *
     * @return the plan
     */
    public MealPlan plan() {
        return plan;
    }

    /**
     * Returns the rule-based plan generator (F05 offline fallback). It builds detached plans from the
     * shared recipe book, pantry, validator and preferences and never changes {@link #plan()} itself.
     *
     * @return the planner
     */
    public RulePlanner planner() {
        return planner;
    }

    /**
     * Returns the command history.
     *
     * @return the history
     */
    public CommandHistory history() {
        return history;
    }

    /**
     * Returns the shopping-list generator for the current plan; it changes when {@link #newPlan} is called.
     *
     * @return the generator
     */
    public ShoppingListGenerator generator() {
        return generator;
    }
}
