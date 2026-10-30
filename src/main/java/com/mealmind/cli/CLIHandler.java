package com.mealmind.cli;

import com.mealmind.app.ApplicationContext;
import com.mealmind.command.AddPantryItemCommand;
import com.mealmind.command.ClearMealCommand;
import com.mealmind.command.RemovePantryItemCommand;
import com.mealmind.command.ReplaceMealCommand;
import com.mealmind.command.ScaleRecipeCommand;
import com.mealmind.config.ConfigurationException;
import com.mealmind.llm.LlmException;
import com.mealmind.model.DayPlan;
import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeScaler;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import com.mealmind.persistence.RepositoryException;
import com.mealmind.shopping.ShoppingListFormatter;
import com.mealmind.shopping.ShoppingListGenerator;
import com.mealmind.validation.ValidationResult;
import com.mealmind.validation.Violation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The interactive command-line interface (REPL). Each line is tokenized, dispatched to a small
 * private method per command, and every change to the model goes through the session's
 * {@code CommandHistory}, so {@code undo} and {@code redo} work the same way for every command.
 * The command syntax follows Stage 1 (F02, F08, F11).
 *
 * <p>Errors are printed as one line ({@code "Error: ..."} or {@code "Usage: ..."}) and the session
 * continues; stack traces are never shown.
 *
 * <p>Profile commands (F01) are handled by {@link ProfileCommands}. Before {@code plan set} changes
 * the plan, the new meal is checked by the {@code ConstraintValidator}: an allergen or diet conflict
 * refuses the change (Stage 1 F06 rule), and target/budget advisories are printed as warnings.
 * The {@code llm} commands are handled by {@link LlmCommands}, and the {@code tools} commands (a
 * developer and demo aid for the agent's read-only tools) by {@link ToolCommands}.
 */
public final class CLIHandler {

    private static final String PROMPT = "mealmind> ";

    private static final String PANTRY_USAGE = "pantry add|list|remove ...";
    private static final String PANTRY_ADD_USAGE = "pantry add \"<name>\" <qty> <unit> [expiry yyyy-MM-dd]";
    private static final String PANTRY_REMOVE_USAGE = "pantry remove \"<name>\"";
    private static final String RECIPE_USAGE = "recipe list|show|scale ...";
    private static final String RECIPE_SHOW_USAGE = "recipe show <id>";
    private static final String RECIPE_SCALE_USAGE = "recipe scale --id <id> --servings <n>";
    private static final String PLAN_USAGE = "plan new|set|clear|show|check ...";
    private static final String PLAN_NEW_USAGE = "plan new --days <n>";
    private static final String PLAN_SET_USAGE = "plan set <day> <slot> <recipeId> [--servings <n>]";
    private static final String PLAN_CLEAR_USAGE = "plan clear <day> <slot>";
    private static final String SHOPPING_USAGE = "shopping list | shopping export --format txt [--file <path>]";
    private static final String SHOPPING_EXPORT_USAGE = "shopping export --format txt [--file <path>]";

    /** Items expiring within this many days are flagged in {@code pantry list}. */
    private static final int EXPIRY_WARNING_DAYS = 3;

    private final ApplicationContext ctx;
    private final BufferedReader in;
    private final PrintStream out;
    private final CLIConsoleView view;
    private final ProfileCommands profileCommands;
    private final LlmCommands llmCommands;
    private final ToolCommands toolCommands;
    private ShoppingListGenerator watchedGenerator;

    /**
     * Creates the handler and subscribes its {@link CLIConsoleView} to the shopping-list generator.
     *
     * @param ctx the session
     * @param in  where commands are read from
     * @param out where output is printed
     * @throws NullPointerException if an argument is null
     */
    public CLIHandler(ApplicationContext ctx, BufferedReader in, PrintStream out) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
        this.view = new CLIConsoleView(out);
        this.profileCommands = new ProfileCommands(ctx.profile(), out);
        this.llmCommands = new LlmCommands(ctx.llm(), out);
        this.toolCommands = new ToolCommands(ctx.tools(), out);
        watch(ctx.generator());
    }

    /** Moves the console view to {@code generator} (needed after {@code plan new}). */
    private void watch(ShoppingListGenerator generator) {
        if (watchedGenerator != null) {
            watchedGenerator.removeObserver(view);
        }
        watchedGenerator = generator;
        generator.addObserver(view);
    }

    /** Reads and runs commands until {@code exit}, {@code quit} or end of input. */
    public void run() {
        while (true) {
            out.print(PROMPT);
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                out.println("Error: could not read input: " + e.getMessage());
                return;
            }
            if (line == null) {
                out.println();
                return;
            }
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                out.println("Goodbye.");
                return;
            }
            if (!trimmed.isEmpty()) {
                handleLine(trimmed);
            }
        }
    }

    /**
     * Runs one command line and prints its result. Problems are printed, never thrown.
     * ({@code exit} and {@code quit} are handled by {@link #run()}.)
     *
     * @param line the command line
     */
    public void handleLine(String line) {
        try {
            // 'tools' takes raw JSON, which the tokenizer would break, so it gets the untokenized rest of the line.
            String[] head = line.strip().split("\\s+", 2);
            if (head[0].equalsIgnoreCase("tools")) {
                toolCommands.handle(head.length > 1 ? head[1] : "");
                return;
            }
            List<String> tokens = CommandLineTokenizer.tokenize(line);
            if (!tokens.isEmpty()) {
                dispatch(tokens.get(0).toLowerCase(Locale.ROOT), tokens.subList(1, tokens.size()));
            }
        } catch (UsageException e) {
            out.println("Usage: " + e.getMessage());
        } catch (IllegalArgumentException | IncompatibleUnitException | NoSuchElementException
                 | IllegalStateException | DateTimeParseException | RepositoryException
                 | LlmException | ConfigurationException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void dispatch(String command, List<String> args) {
        switch (command) {
            case "help" -> help();
            case "exit", "quit" -> { }
            case "pantry" -> pantry(args);
            case "profile" -> profileCommands.handle(args);
            case "llm" -> llmCommands.handle(args);
            case "recipe" -> recipe(args);
            case "plan" -> plan(args);
            case "shopping" -> shopping(args);
            case "undo" -> undo();
            case "redo" -> redo();
            default -> out.println("Unknown command: " + command + ". Type 'help' for commands.");
        }
    }

    private static String subcommand(List<String> args, String usage) {
        if (args.isEmpty()) {
            throw new UsageException(usage);
        }
        return args.get(0).toLowerCase(Locale.ROOT);
    }

    // ---- help, undo, redo ----

    private void help() {
        out.println("Commands:");
        out.println("  " + PANTRY_ADD_USAGE);
        out.println("  pantry list");
        out.println("  " + PANTRY_REMOVE_USAGE);
        out.println("  recipe list");
        out.println("  " + RECIPE_SHOW_USAGE);
        out.println("  " + RECIPE_SCALE_USAGE);
        out.println("  " + PLAN_NEW_USAGE);
        out.println("  " + PLAN_SET_USAGE + "   (slot: breakfast, lunch, dinner, snack)");
        out.println("  " + PLAN_CLEAR_USAGE);
        out.println("  plan show");
        out.println("  plan check");
        out.println("  profile show");
        out.println("  " + ProfileCommands.DIET_USAGE);
        out.println("  " + ProfileCommands.ALLERGY_USAGE);
        out.println("  " + ProfileCommands.UPDATE_USAGE);
        out.println("  llm status");
        out.println("  llm ping   (sends one tiny request to the configured AI provider)");
        out.println("  tools list");
        out.println("  tools schema");
        out.println("  tools call <name> [json]   (runs one read-only agent tool, e.g. tools call pantry_query {})");
        out.println("  shopping list");
        out.println("  " + SHOPPING_EXPORT_USAGE);
        out.println("  undo");
        out.println("  redo");
        out.println("  help");
        out.println("  exit | quit");
    }

    private void undo() {
        Optional<String> description = ctx.history().nextUndoDescription();
        out.println(ctx.history().undo() ? "Undone: " + description.orElseThrow() : "Nothing to undo.");
    }

    private void redo() {
        Optional<String> description = ctx.history().nextRedoDescription();
        out.println(ctx.history().redo() ? "Redone: " + description.orElseThrow() : "Nothing to redo.");
    }

    // ---- pantry (F02) ----

    private void pantry(List<String> args) {
        List<String> rest = args.isEmpty() ? args : args.subList(1, args.size());
        switch (subcommand(args, PANTRY_USAGE)) {
            case "add" -> pantryAdd(rest);
            case "list" -> pantryList();
            case "remove" -> pantryRemove(rest);
            default -> throw new UsageException(PANTRY_USAGE);
        }
    }

    private void pantryAdd(List<String> args) {
        List<String> words = CliArgs.parse(args, Set.of(), PANTRY_ADD_USAGE)
                .expectPositional(3, 4, PANTRY_ADD_USAGE).positional();
        double quantity = CliArgs.parseDouble(words.get(1), PANTRY_ADD_USAGE);
        Unit unit = Unit.parse(words.get(2));
        LocalDate expiry = words.size() == 4 ? LocalDate.parse(words.get(3)) : null;
        PantryItem item = new PantryItem(words.get(0), quantity, unit, expiry);

        ctx.history().pushAndExecute(new AddPantryItemCommand(ctx.pantry(), item));
        PantryItem stored = ctx.pantry().getItem(item.getName()).orElseThrow();
        out.println("Added " + item.getName() + ". Pantry now has "
                + CliFormat.amount(stored.getQuantity(), stored.getUnit()) + ".");
    }

    private void pantryList() {
        List<PantryItem> items = ctx.pantry().getItems();
        if (items.isEmpty()) {
            out.println("Pantry is empty.");
            return;
        }
        LocalDate today = LocalDate.now(ctx.clock());
        out.println(String.format(Locale.ROOT, "%-28s %-12s %s", "Item", "Quantity", "Expiry"));
        for (PantryItem item : items) {
            out.println(String.format(Locale.ROOT, "%-28s %-12s %s", item.getName(),
                    CliFormat.amount(item.getQuantity(), item.getUnit()), expiryText(item, today)));
        }
    }

    private static String expiryText(PantryItem item, LocalDate today) {
        if (item.getExpiry().isEmpty()) {
            return "-";
        }
        String date = item.getExpiry().get().toString();
        long days = item.daysUntilExpiry(today);
        if (days < 0) {
            return date + "  EXPIRED";
        }
        if (days == 0) {
            return date + "  expires today";
        }
        if (days <= EXPIRY_WARNING_DAYS) {
            return date + "  expires in " + days + (days == 1 ? " day" : " days");
        }
        return date;
    }

    private void pantryRemove(List<String> args) {
        String name = CliArgs.parse(args, Set.of(), PANTRY_REMOVE_USAGE)
                .expectPositional(1, 1, PANTRY_REMOVE_USAGE).positional().get(0);
        ctx.history().pushAndExecute(new RemovePantryItemCommand(ctx.pantry(), name));
        out.println("Removed " + name + " from pantry.");
    }

    // ---- recipes (F08) ----

    private void recipe(List<String> args) {
        List<String> rest = args.isEmpty() ? args : args.subList(1, args.size());
        switch (subcommand(args, RECIPE_USAGE)) {
            case "list" -> recipeList();
            case "show" -> recipeShow(rest);
            case "scale" -> recipeScale(rest);
            default -> throw new UsageException(RECIPE_USAGE);
        }
    }

    private Recipe findRecipe(String id) {
        return ctx.recipeBook().get(id)
                .orElseThrow(() -> new NoSuchElementException("No recipe with id '" + id + "'. Try 'recipe list'."));
    }

    private void recipeList() {
        for (Recipe recipe : ctx.recipeBook().getAll()) {
            out.println(String.format(Locale.ROOT, "%-6s %-36s %-11s %3d min  %4.0f kcal/serving",
                    recipe.getId(), recipe.getName(), servings(recipe.getServings()), recipe.getPrepMinutes(),
                    recipe.caloriesPerServing()));
        }
    }

    private void recipeShow(List<String> args) {
        String id = CliArgs.parse(args, Set.of(), RECIPE_SHOW_USAGE)
                .expectPositional(1, 1, RECIPE_SHOW_USAGE).positional().get(0);
        Recipe recipe = findRecipe(id);
        out.println(recipe.getId() + " " + recipe.getName());
        out.println("Servings: " + recipe.getServings() + " | Prep: " + recipe.getPrepMinutes() + " min");
        printPerServingAndIngredients(recipe);
        out.println("Steps:");
        List<String> steps = recipe.getInstructions();
        for (int i = 0; i < steps.size(); i++) {
            out.println("  " + (i + 1) + ". " + steps.get(i));
        }
        List<Violation> problems = ctx.validator().validateAgainstBlacklists(recipe).getSafetyViolations();
        if (problems.isEmpty()) {
            out.println("Profile check: OK");
        }
        problems.forEach(problem -> out.println("Profile check: " + problem.message()));
    }

    private void printPerServingAndIngredients(Recipe recipe) {
        out.println("Per serving: " + CliFormat.nutrition(recipe, recipe.getServings()));
        out.println("Ingredients:");
        for (Ingredient ingredient : recipe.getIngredients()) {
            out.println("  - " + CliFormat.number(ingredient.getQuantity(), ingredient.getUnit()) + " "
                    + ingredient.getUnit().getSymbol() + " " + ingredient.getName());
        }
    }

    private void recipeScale(List<String> args) {
        CliArgs parsed = CliArgs.parse(args, Set.of("id", "servings"), RECIPE_SCALE_USAGE)
                .expectPositional(0, 0, RECIPE_SCALE_USAGE);
        String id = parsed.requireFlag("id", RECIPE_SCALE_USAGE);
        int servings = CliArgs.parseInt(parsed.requireFlag("servings", RECIPE_SCALE_USAGE), RECIPE_SCALE_USAGE);
        findRecipe(id);

        ctx.history().pushAndExecute(new ScaleRecipeCommand(ctx.recipeBook(), id, servings));
        Recipe scaled = findRecipe(id);
        out.println("Scaled " + scaled.getName() + " to " + scaled.getServings()
                + " servings (per-serving values unchanged).");
        printPerServingAndIngredients(scaled);
    }

    // ---- meal plan ----

    private void plan(List<String> args) {
        List<String> rest = args.isEmpty() ? args : args.subList(1, args.size());
        switch (subcommand(args, PLAN_USAGE)) {
            case "new" -> planNew(rest);
            case "set" -> planSet(rest);
            case "clear" -> planClear(rest);
            case "show" -> planShow();
            case "check" -> planCheck();
            default -> throw new UsageException(PLAN_USAGE);
        }
    }

    private void planNew(List<String> args) {
        CliArgs parsed = CliArgs.parse(args, Set.of("days"), PLAN_NEW_USAGE).expectPositional(0, 0, PLAN_NEW_USAGE);
        int days = CliArgs.parseInt(parsed.requireFlag("days", PLAN_NEW_USAGE), PLAN_NEW_USAGE);
        MealPlan plan = ctx.newPlan(days);
        watch(ctx.generator());
        out.println("Started a new " + plan.getNumberOfDays() + "-day plan. Undo history cleared.");
    }

    private void planSet(List<String> args) {
        CliArgs parsed = CliArgs.parse(args, Set.of("servings"), PLAN_SET_USAGE).expectPositional(3, 3, PLAN_SET_USAGE);
        int day = CliArgs.parseInt(parsed.positional().get(0), PLAN_SET_USAGE);
        MealType type = MealType.parse(parsed.positional().get(1));
        Recipe recipe = findRecipe(parsed.positional().get(2));
        if (parsed.flags().containsKey("servings")) {
            // A scaled copy for this meal only; the recipe book keeps the original.
            recipe = RecipeScaler.scale(recipe, CliArgs.parseInt(parsed.flags().get("servings"), PLAN_SET_USAGE));
        }
        // Labelled with the recipe name so undo/redo descriptions read "Replace Day 1 DINNER with <recipe>".
        Meal meal = new Meal(type, recipe.getName(), List.of(recipe));

        // Stage 1 F06 rule: a safety violation aborts the edit and leaves the plan untouched.
        List<Violation> unsafe = ctx.validator().validateAgainstBlacklists(meal).getSafetyViolations();
        if (!unsafe.isEmpty()) {
            unsafe.forEach(violation -> out.println("Refused: " + violation.message()));
            return;
        }
        ctx.history().pushAndExecute(new ReplaceMealCommand(ctx.plan(), day, type, meal));
        out.println("Day " + day + " " + type.getDisplayName() + ": " + describe(meal));
        ctx.validator().validateMacrosAndAllergens(ctx.plan()).getAdvisories()
                .forEach(advisory -> out.println("Warning: " + advisory.message()));
    }

    private void planClear(List<String> args) {
        List<String> words = CliArgs.parse(args, Set.of(), PLAN_CLEAR_USAGE)
                .expectPositional(2, 2, PLAN_CLEAR_USAGE).positional();
        int day = CliArgs.parseInt(words.get(0), PLAN_CLEAR_USAGE);
        MealType type = MealType.parse(words.get(1));
        ctx.history().pushAndExecute(new ClearMealCommand(ctx.plan(), day, type));
        out.println("Cleared Day " + day + " " + type.getDisplayName() + ".");
    }

    private void planShow() {
        MealPlan plan = ctx.plan();
        out.println(plan.getName() + " (" + plan.getNumberOfDays() + " days)");
        for (DayPlan day : plan.getDays()) {
            out.println(day.getName() + ":");
            for (MealType type : MealType.values()) {
                String content = day.getMeal(type).map(CLIHandler::describe).orElse("(empty)");
                out.println("  " + type.getDisplayName() + ": " + content);
            }
        }
        out.println("Plan totals: " + CliFormat.nutrition(plan, 1));
        printViolations(ctx.validator().validateMacrosAndAllergens(plan));
    }

    private void planCheck() {
        ValidationResult result = ctx.validator().validateMacrosAndAllergens(ctx.plan());
        if (result.isClean()) {
            out.println("Plan is safe and within your targets.");
            return;
        }
        printViolations(result);
        out.println("Summary: " + result.summary());
    }

    /** Prints each violation as "SAFETY: ..." or "Advisory: ...". */
    private void printViolations(ValidationResult result) {
        result.getSafetyViolations().forEach(v -> out.println("SAFETY: " + v.message()));
        result.getAdvisories().forEach(v -> out.println("Advisory: " + v.message()));
    }

    /**
     * Describes a meal as its recipes with servings, e.g. {@code "Chicken Stir-Fry (2 servings)"}, prefixed
     * by the meal's label when that label says something extra (it is not the slot name or the recipe name).
     */
    private static String describe(Meal meal) {
        String names = meal.getRecipes().isEmpty()
                ? meal.getIngredients().stream().map(Ingredient::getName).collect(Collectors.joining(", "))
                : meal.getRecipes().stream().map(Recipe::getName).collect(Collectors.joining(", "));
        String contents = meal.getRecipes().isEmpty()
                ? names
                : meal.getRecipes().stream().map(r -> r.getName() + " (" + servings(r.getServings()) + ")")
                        .collect(Collectors.joining(", "));
        String label = meal.getName();
        boolean labelAddsNothing = label.equals(meal.getType().getDisplayName()) || label.equals(names);
        return labelAddsNothing ? contents : label + " - " + contents;
    }

    private static String servings(int count) {
        return count + (count == 1 ? " serving" : " servings");
    }

    // ---- shopping list (F11) ----

    private void shopping(List<String> args) {
        List<String> rest = args.isEmpty() ? args : args.subList(1, args.size());
        switch (subcommand(args, SHOPPING_USAGE)) {
            case "list" -> out.print(ShoppingListFormatter.toText(ctx.generator().getCurrent()));
            case "export" -> shoppingExport(rest);
            default -> throw new UsageException(SHOPPING_USAGE);
        }
    }

    private void shoppingExport(List<String> args) {
        CliArgs parsed = CliArgs.parse(args, Set.of("format", "file"), SHOPPING_EXPORT_USAGE)
                .expectPositional(0, 0, SHOPPING_EXPORT_USAGE);
        String format = parsed.requireFlag("format", SHOPPING_EXPORT_USAGE);
        if (!format.equalsIgnoreCase("txt")) {
            throw new IllegalArgumentException("Unsupported format '" + format + "'. Supported format: txt");
        }
        String text = ShoppingListFormatter.toText(ctx.generator().getCurrent());
        String file = parsed.flags().get("file");
        if (file == null) {
            out.print(text);
            return;
        }
        try {
            Files.writeString(Path.of(file), text);
            out.println("Shopping list written to " + file);
        } catch (IOException e) {
            out.println("Error: could not write " + file + ": " + e.getMessage());
        }
    }
}
