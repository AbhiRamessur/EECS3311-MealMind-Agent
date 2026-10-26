package com.mealmind.app;

import com.mealmind.command.CommandHistory;
import com.mealmind.model.MealPlan;
import com.mealmind.model.RecipeBook;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.persistence.PantryRepository;
import com.mealmind.persistence.RecipeRepository;
import com.mealmind.persistence.RepositoryException;
import com.mealmind.shopping.ShoppingListGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Holds the shared model objects for one MealMind session: the pantry, recipe book, current meal
 * plan, command history and shopping-list generator, plus the clock and data directory. The CLI (and
 * later the GUI and the agent facade) work through this one object instead of creating their own.
 *
 * <p><strong>Persistence:</strong> the pantry is loaded from {@code <dataDir>/pantry.json} and saved
 * back after every change by an observer on the pantry (the Observer pattern used for persistence).
 * A failed save is reported through the warning sink and never stops the session. Recipes come from
 * the bundled sample data. The meal plan and recipe-book changes (such as scaling) are kept in memory
 * only; saving them is deferred to a later commit.
 */
public final class ApplicationContext {

    /** Name of the plan created at startup and by {@link #newPlan}. */
    public static final String PLAN_NAME = "Weekly Plan";

    private static final int DEFAULT_PLAN_DAYS = 7;
    private static final String PANTRY_FILE = "pantry.json";

    private final Clock clock;
    private final Path dataDir;
    private final Pantry pantry;
    private final RecipeBook recipeBook;
    private final CommandHistory history;
    private MealPlan plan;
    private ShoppingListGenerator generator;
    private Consumer<String> warningSink = System.err::println;

    private ApplicationContext(Clock clock, Path dataDir, Pantry pantry, RecipeBook recipeBook) {
        this.clock = clock;
        this.dataDir = dataDir;
        this.pantry = pantry;
        this.recipeBook = recipeBook;
        this.history = new CommandHistory();
        this.plan = new MealPlan(PLAN_NAME, DEFAULT_PLAN_DAYS);
        this.generator = new ShoppingListGenerator(plan, pantry);
    }

    /**
     * Creates a session: makes {@code dataDir} if needed, loads the sample recipes and the saved
     * pantry (a missing pantry file means an empty pantry), creates an empty 7-day plan, the command
     * history and the shopping-list generator, and only then starts autosaving the pantry, so loading
     * never triggers a save.
     *
     * @param dataDir where user data is stored
     * @param clock   the clock used for "today" (expiry checks)
     * @return the new context
     * @throws RepositoryException if the data directory cannot be created or saved data is invalid
     * @throws NullPointerException if an argument is null
     */
    public static ApplicationContext create(Path dataDir, Clock clock) {
        Objects.requireNonNull(dataDir, "dataDir");
        Objects.requireNonNull(clock, "clock");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RepositoryException("Could not create data directory " + dataDir + ": " + e.getMessage(), e);
        }

        RecipeBook recipeBook = new RecipeBook();
        RecipeRepository.loadFromResource(RecipeRepository.SAMPLE_RECIPES).forEach(recipeBook::put);
        Pantry pantry = new Pantry();
        for (PantryItem item : PantryRepository.load(dataDir.resolve(PANTRY_FILE))) {
            pantry.replaceItem(item);
        }

        ApplicationContext context = new ApplicationContext(clock, dataDir, pantry, recipeBook);
        pantry.addObserver(event -> context.savePantry());
        return context;
    }

    private void savePantry() {
        try {
            PantryRepository.save(pantryFile(), pantry.getItems());
        } catch (RepositoryException e) {
            warningSink.accept("Warning: pantry changes were not saved. " + e.getMessage());
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
