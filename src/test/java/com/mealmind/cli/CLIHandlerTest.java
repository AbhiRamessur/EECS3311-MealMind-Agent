package com.mealmind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealmind.app.ApplicationContext;
import com.mealmind.llm.LLMClient;
import com.mealmind.llm.LlmErrorKind;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.LlmRequest;
import com.mealmind.llm.LlmResponse;
import com.mealmind.llm.TokenUsage;
import com.mealmind.model.MealType;
import com.mealmind.shopping.ShoppingListFormatter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Scripted CLI sessions against a fresh context; assertions check key output fragments. */
class CLIHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private ApplicationContext ctx;
    private ByteArrayOutputStream buffer;
    private CLIHandler handler;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.create(dir.resolve("data"), CLOCK);
        buffer = new ByteArrayOutputStream();
        handler = new CLIHandler(ctx, new BufferedReader(new StringReader("")),
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    /** Runs the lines and returns only the output they produced. */
    private String run(String... lines) {
        buffer.reset();
        for (String line : lines) {
            handler.handleLine(line);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void assertContains(String output, String expected) {
        assertTrue(output.contains(expected), () -> "Expected <" + expected + "> in:\n" + output);
    }

    // ---- pantry ----

    @Test
    void pantryAddThenListShowsItem() {
        String output = run("pantry add \"Basmati Rice\" 500 g", "pantry list");

        assertContains(output, "Added Basmati Rice. Pantry now has 500 g.");
        assertContains(output, "Basmati Rice");
        assertContains(output, "500 g");
    }

    @Test
    void addingSameItemMergesAndReportsTotal() {
        assertContains(run("pantry add Rice 500 g", "pantry add rice 1 kg"), "Pantry now has 1.5 kg.");
    }

    @Test
    void incompatibleUnitPrintsErrorAndLeavesPantryUnchanged() {
        run("pantry add Rice 500 g");

        assertContains(run("pantry add Rice 1 l"), "Error: Cannot convert");
        assertEquals(500, ctx.pantry().getItem("rice").orElseThrow().getQuantity(), 1e-9);
    }

    @Test
    void pantryListAnnotatesExpiry() {
        String output = run("pantry add Milk 1 l 2026-10-25", "pantry add Yogurt 2 pc 2026-10-28",
                "pantry add Salt 1 kg", "pantry list");

        assertContains(output, "2026-10-25  EXPIRED");
        assertContains(output, "2026-10-28  expires in 2 days");
        assertContains(run("pantry list"), "Salt");
    }

    @Test
    void emptyPantryListSaysSo() {
        assertContains(run("pantry list"), "Pantry is empty.");
    }

    @Test
    void pantryRemoveAndMissingItem() {
        run("pantry add Rice 500 g");

        assertContains(run("pantry remove \"rice\""), "Removed rice from pantry.");
        assertTrue(ctx.pantry().isEmpty());
        assertContains(run("pantry remove Tofu"), "Error: No pantry item named 'Tofu'");
    }

    @Test
    void invalidDateIsAnError() {
        assertContains(run("pantry add Milk 1 l 2026-13-40"), "Error: ");
        assertTrue(ctx.pantry().isEmpty());
    }

    // ---- recipes ----

    @Test
    void recipeListShowsTenRecipes() {
        String output = run("recipe list");

        for (int id = 101; id <= 110; id++) {
            assertContains(output, "R" + id);
        }
        assertEquals(10, output.lines().count());
    }

    @Test
    void recipeShowPrintsPerServingValuesAndNumberedSteps() {
        String output = run("recipe show R101");

        assertContains(output, "R101 Chicken Stir-Fry");
        assertContains(output, "Per serving: 707 kcal, 58.1 g protein");
        assertContains(output, "  - 300 g Chicken breast");
        assertContains(output, "  1. Cook the rice");
        assertContains(output, "  4. Stir in the soy sauce");
    }

    @Test
    void unknownRecipeIsAnError() {
        assertContains(run("recipe show R999"), "Error: No recipe with id 'R999'");
    }

    @Test
    void recipeScaleDoublesQuantitiesKeepsPerServingAndUndoRestores() {
        String scaled = run("recipe scale --id R101 --servings 4");

        assertContains(scaled, "Scaled Chicken Stir-Fry to 4 servings");
        assertContains(scaled, "Per serving: 707 kcal");
        assertContains(scaled, "  - 600 g Chicken breast");

        assertContains(run("undo"), "Undone: Scale Chicken Stir-Fry to 4 servings");
        String shown = run("recipe show R101");
        assertContains(shown, "Servings: 2");
        assertContains(shown, "  - 300 g Chicken breast");
    }

    // ---- plan ----

    @Test
    void planSetAndShow() {
        assertContains(run("plan set 1 dinner R101"), "Day 1 Dinner: Chicken Stir-Fry (2 servings)");

        String shown = run("plan show");
        assertContains(shown, "Weekly Plan (7 days)");
        assertContains(shown, "  Dinner: Chicken Stir-Fry (2 servings)");
        assertContains(shown, "  Breakfast: (empty)");
        assertContains(shown, "Plan totals: 1414 kcal");
    }

    @Test
    void planSetWithServingsDoesNotChangeRecipeBook() {
        assertContains(run("plan set 2 lunch R103 --servings 2"), "Day 2 Lunch: Red Lentil Soup (2 servings)");

        assertEquals(4, ctx.recipeBook().get("R103").orElseThrow().getServings());
        assertEquals(2, ctx.plan().getMeal(2, MealType.LUNCH).orElseThrow().getRecipes().get(0).getServings());
    }

    @Test
    void invalidPlanArgumentsAreErrors() {
        assertContains(run("plan set 9 dinner R101"), "Error: Day must be between 1 and 7");
        assertContains(run("plan set 1 brunch R101"), "Error: Unknown meal type 'brunch'");
        assertContains(run("plan set 1 dinner R999"), "Error: No recipe with id 'R999'");
        assertContains(run("plan set one dinner R101"), "Usage: plan set");
        assertEquals(28, ctx.plan().getEmptySlots().size());
    }

    @Test
    void planClearRemovesMealAndRejectsEmptySlot() {
        run("plan set 1 dinner R101");

        assertContains(run("plan clear 1 dinner"), "Cleared Day 1 Dinner.");
        assertContains(run("plan clear 1 dinner"), "Error: Day 1 DINNER is already empty");
        assertContains(run("undo"), "Undone: Clear Day 1 DINNER");
        assertTrue(ctx.plan().getMeal(1, MealType.DINNER).isPresent());
    }

    // ---- shopping list (F02 -> F11 end to end) ----

    @Test
    void shoppingListShrinksWhenPantryStockIsAdded() {
        run("plan set 1 dinner R101");
        String before = run("shopping list");
        assertContains(before, "! Pantry is empty: all ingredients are listed as missing.");
        assertContains(before, "- 300 g Chicken breast");

        String added = run("pantry add \"Chicken breast\" 100 g");
        assertContains(added, "[update] Shopping list updated (7 items)");

        String after = run("shopping list");
        assertContains(after, "- 200 g Chicken breast");
        assertFalse(after.contains("Pantry is empty"));
    }

    @Test
    void shoppingExportPrintsWritesAndRejectsOtherFormats() throws IOException {
        run("plan set 1 dinner R101");

        assertContains(run("shopping export --format txt"), "MealMind Shopping List");

        Path file = dir.resolve("list.txt");
        assertContains(run("shopping export --format txt --file \"" + file + "\""), "Shopping list written to");
        assertEquals(ShoppingListFormatter.toText(ctx.generator().getCurrent()), Files.readString(file));

        assertContains(run("shopping export --format csv"), "Error: Unsupported format 'csv'. Supported format: txt");
        assertContains(run("shopping export"), "Usage: shopping export");
    }

    // ---- undo / redo ----

    @Test
    void undoAndRedoWorkAcrossPantryPlanAndRecipes() {
        assertContains(run("undo"), "Nothing to undo.");
        run("pantry add Rice 500 g", "plan set 1 dinner R101", "recipe scale --id R102 --servings 2");

        assertContains(run("undo"), "Undone: Scale Berry Overnight Oats to 2 servings");
        assertContains(run("undo"), "Undone: Replace Day 1 DINNER with Chicken Stir-Fry");
        assertContains(run("undo"), "Undone: Add Rice to pantry");
        assertContains(run("undo"), "Nothing to undo.");
        assertTrue(ctx.pantry().isEmpty());
        assertTrue(ctx.plan().getMeal(1, MealType.DINNER).isEmpty());
        assertEquals(1, ctx.recipeBook().get("R102").orElseThrow().getServings());

        assertContains(run("redo"), "Redone: Add Rice to pantry");
        run("redo", "redo");
        assertContains(run("redo"), "Nothing to redo.");
        assertEquals(2, ctx.recipeBook().get("R102").orElseThrow().getServings());
        assertTrue(ctx.plan().getMeal(1, MealType.DINNER).isPresent());
    }

    @Test
    void planNewReplacesPlanClearsHistoryAndShoppingListFollows() {
        run("pantry add Rice 500 g", "plan set 1 dinner R101");

        assertContains(run("plan new --days 3"), "Started a new 3-day plan. Undo history cleared.");
        assertContains(run("undo"), "Nothing to undo.");
        assertContains(run("plan show"), "Weekly Plan (3 days)");
        assertContains(run("shopping list"), "Nothing to buy.");

        String set = run("plan set 2 lunch R105");
        assertEquals(1, set.lines().filter(line -> line.startsWith("[update]")).count());
        assertContains(run("shopping list"), "Black beans");
        assertContains(run("plan new --days 8"), "Error: Number of days must be between 1 and 7");
    }

    // ---- general ----

    @Test
    void unknownCommandsAndBadArgumentsKeepSessionRunning() {
        assertContains(run("dance"), "Unknown command: dance. Type 'help' for commands.");
        assertContains(run("pantry add"), "Usage: pantry add \"<name>\" <qty> <unit> [expiry yyyy-MM-dd]");
        assertContains(run("pantry add Rice lots g"), "Usage: pantry add");
        assertContains(run("recipe scale --id R101"), "Usage: recipe scale --id <id> --servings <n>");
        assertContains(run("recipe scale --id R101 --servings 2 --color red"), "Usage: recipe scale");
        assertContains(run("pantry"), "Usage: pantry");
        assertContains(run("pantry add \"Rice 500 g"), "Error: Unterminated quote");
        assertContains(run("recipe list"), "R101");
    }

    @Test
    void helpListsEveryCommand() {
        String output = run("help");

        for (String command : new String[] {"pantry add", "pantry list", "pantry remove", "recipe list",
                "recipe show", "recipe scale", "plan new", "plan set", "plan clear", "plan show",
                "shopping list", "shopping export", "undo", "redo", "help", "exit"}) {
            assertContains(output, command);
        }
    }

    @Test
    void runStopsAtExitAndAtEndOfInput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream print = new PrintStream(out, true, StandardCharsets.UTF_8);
        new CLIHandler(ctx, new BufferedReader(new StringReader("pantry add Rice 1 kg\n\nexit\nrecipe list\n")), print)
                .run();
        String output = out.toString(StandardCharsets.UTF_8);
        assertContains(output, "mealmind> ");
        assertContains(output, "Added Rice");
        assertContains(output, "Goodbye.");
        assertFalse(output.contains("R101"));

        ByteArrayOutputStream eofOut = new ByteArrayOutputStream();
        new CLIHandler(ctx, new BufferedReader(new StringReader("help")), new PrintStream(eofOut, true, StandardCharsets.UTF_8))
                .run();
        assertContains(eofOut.toString(StandardCharsets.UTF_8), "Commands:");
    }

    // ---- profile (F01) and safety checks ----

    @Test
    void profileShowOnDefaults() {
        String output = run("profile show");

        assertContains(output, "Diet: Omnivore");
        assertContains(output, "Allergens: none");
        assertContains(output, "Calorie target: not set");
        assertContains(output, "Protein target: not set");
        assertContains(output, "Weekly budget: not set");
        assertContains(output, "Household servings: 1");
    }

    @Test
    void dietAndAllergyChangesAppearInProfileShow() {
        assertContains(run("profile diet Vegetarian"), "Diet set to Vegetarian.");
        assertContains(run("profile allergy add \"Tree Nuts\""), "Allergen added: tree nuts");
        assertContains(run("profile allergy add peanut"), "Allergen added: peanut");
        assertContains(run("profile allergy add PEANUT"), "Allergen already listed: peanut");

        String shown = run("profile show");
        assertContains(shown, "Diet: Vegetarian");
        assertContains(shown, "Allergens: peanut, tree nuts");

        assertContains(run("profile allergy remove peanut"), "Allergen removed: peanut");
        assertContains(run("profile allergy remove peanut"), "Allergen not listed: peanut");
        assertContains(run("profile diet keto"), "Error: Unknown diet 'keto'");
    }

    @Test
    void profileUpdateSetsClearsAndValidatesAllBeforeApplying() {
        assertContains(run("profile update --calories 2000 --protein 120 --budget 100 --servings 2"),
                "Profile updated: calories 2000, protein 120, budget 100, servings 2");
        String shown = run("profile show");
        assertContains(shown, "Calorie target: 2000 kcal/day");
        assertContains(shown, "Protein target: 120 g/day");
        assertContains(shown, "Weekly budget: $100.00");
        assertContains(shown, "Household servings: 2");

        assertContains(run("profile update --calories none"), "Profile updated: calories cleared");
        assertContains(run("profile update"), "Usage: profile update");
        assertContains(run("profile update --calories lots"), "Usage: profile update");

        assertContains(run("profile update --calories 1800 --budget -5"), "Error: Weekly budget must be");
        assertTrue(ctx.profile().getCalorieTarget().isEmpty(), "a bad flag must change nothing");
        assertEquals(100.0, ctx.profile().getWeeklyBudget().orElseThrow());
    }

    @Test
    void planSetRefusesAllergenAndLeavesPlanAndHistoryUntouched() {
        run("profile allergy add peanut");

        String output = run("plan set 1 snack R109");

        assertContains(output, "Refused: Peanut butter contains peanut, a listed allergen");
        assertFalse(output.contains("[update]"));
        assertTrue(ctx.plan().getMeal(1, MealType.SNACK).isEmpty());
        assertEquals(0, ctx.history().undoSize());
        assertContains(run("undo"), "Nothing to undo.");
    }

    @Test
    void vegetarianProfileRefusesMeatRecipeAndAcceptsSafeOne() {
        run("profile diet vegetarian");

        assertContains(run("plan set 1 dinner R101"), "Refused: Chicken breast is not allowed for a vegetarian diet");
        assertTrue(ctx.plan().getMeal(1, MealType.DINNER).isEmpty());

        assertContains(run("plan set 1 dinner R103"), "Day 1 Dinner: Red Lentil Soup (4 servings)");
        assertContains(run("undo"), "Undone: Replace Day 1 DINNER with Red Lentil Soup");
    }

    @Test
    void profileChangeAfterPlanningIsReportedByPlanCheckAndShow() {
        run("plan set 1 dinner R101");
        assertContains(run("plan check"), "Plan is safe and within your targets.");

        run("profile diet vegetarian");

        String check = run("plan check");
        assertContains(check, "SAFETY: Chicken breast is not allowed for a vegetarian diet");
        assertContains(check, "Summary: 1 safety issue");
        assertContains(run("plan show"), "SAFETY: Chicken breast is not allowed for a vegetarian diet");
    }

    @Test
    void advisoriesPrintAsWarningsAfterPlanSet() {
        run("profile update --calories 1200");

        String output = run("plan set 1 dinner R101");

        assertContains(output, "Day 1 Dinner: Chicken Stir-Fry (2 servings)");
        assertContains(output, "Warning: Day 1: 1414 kcal is above 110% of your 1200 kcal target");
        assertContains(run("plan check"), "Advisory: Day 1: 1414 kcal");
        assertContains(run("plan show"), "Advisory: Day 1: 1414 kcal");
    }

    @Test
    void recipeShowIncludesProfileCheck() {
        assertContains(run("recipe show R109"), "Profile check: OK");

        run("profile allergy add peanut");

        assertContains(run("recipe show R109"), "Profile check: Peanut butter contains peanut, a listed allergen");
        assertContains(run("recipe show R103"), "Profile check: OK");
    }

    @Test
    void unknownProfileSubcommandPrintsUsage() {
        assertContains(run("profile frobnicate"), "Usage: profile show | profile diet <type>");
        assertContains(run("profile"), "Usage: profile show");
        assertContains(run("profile allergy list"), "Usage: profile allergy add|remove");
    }

    // ---- llm commands (fake client, no network) ----

    /** A fake AI provider that answers with a fixed reply, or fails when {@code failure} is set. */
    private static final class FakeClient implements LLMClient {
        private final LlmErrorKind failure;
        LlmRequest lastRequest;

        FakeClient(LlmErrorKind failure) {
            this.failure = failure;
        }

        @Override
        public String providerName() {
            return "FakeAI";
        }

        @Override
        public String modelName() {
            return "fake-model";
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            lastRequest = request;
            if (failure != null) {
                throw new LlmException(failure, "The AI service rejected the API key (HTTP 401)", null);
            }
            return new LlmResponse("  OK  ", new TokenUsage(14, 1), "fake-model");
        }
    }

    @Test
    void llmStatusWhenDisabledShowsReason() {
        ctx.llm().disable("No OpenAI API key configured");

        String output = run("llm status");

        assertContains(output, "Provider: none");
        assertContains(output, "Status: disabled (No OpenAI API key configured)");
        assertContains(output, "AI calls: 0 ok, 0 failed; tokens: 0 prompt + 0 completion = 0 total");
    }

    @Test
    void llmPingSuccessUpdatesStatus() {
        FakeClient client = new FakeClient(null);
        ctx.llm().useClient(client);
        assertContains(run("llm status"), "Provider: FakeAI");
        assertContains(run("llm status"), "Model: fake-model");

        String ping = run("llm ping");

        assertContains(ping, "AI reply: OK");
        assertContains(ping, "Tokens: 14 prompt + 1 completion");
        assertEquals(8, client.lastRequest.maxTokens());
        assertEquals(0.0, client.lastRequest.temperature());
        assertContains(run("llm status"), "AI calls: 1 ok, 0 failed; tokens: 14 prompt + 1 completion = 15 total");
    }

    @Test
    void llmPingFailureIsReportedAndCounted() {
        ctx.llm().useClient(new FakeClient(LlmErrorKind.AUTH));

        assertContains(run("llm ping"), "Error: The AI service rejected the API key (HTTP 401)");
        assertContains(run("llm status"), "AI calls: 0 ok, 1 failed");
    }

    @Test
    void llmPingWhenDisabledPrintsDisabledError() {
        assertContains(run("llm ping"), "Error: AI features are disabled: Not configured");
    }

    @Test
    void unknownLlmSubcommandPrintsUsageAndHelpListsLlm() {
        assertContains(run("llm use ollama"), "Usage: llm status | llm ping");
        assertContains(run("llm"), "Usage: llm status | llm ping");
        String help = run("help");
        assertContains(help, "llm status");
        assertContains(help, "llm ping");
    }

    // ---- tools commands (deterministic tool layer, no AI) ----

    @Test
    void toolsListAndSchema() throws Exception {
        String list = run("tools list");
        for (String name : new String[] {"check_safety(", "pantry_query(", "plan_show(", "recipe_search(", "shopping_list("}) {
            assertContains(list, name);
        }
        assertEquals(10, list.lines().count());

        String schema = run("tools schema");
        JsonNode parsed = new ObjectMapper().readTree(schema);
        assertEquals(5, parsed.size());
        assertContains(schema, "\"recipe_search\"");
    }

    @Test
    void toolsCallPantryQueryReflectsPantry() {
        assertContains(run("tools call pantry_query"), "\"count\" : 0");

        run("pantry add Rice 500 g");

        String result = run("tools call pantry_query {\"ingredient\": \"rice\"}");
        assertContains(result, "\"inPantry\" : true");
        assertContains(result, "\"quantity\" : 500.0");
    }

    @Test
    void toolsCallRecipeSearchWithArguments() {
        String result = run("tools call recipe_search {\"query\":\"lentil\",\"pantryFirst\":true}");

        assertContains(result, "\"id\" : \"R103\"");
        assertContains(result, "\"excludedForSafety\" : 0");
    }

    @Test
    void vegetarianProfileHidesMeatRecipeFromSearch() {
        assertContains(run("tools call recipe_search {\"query\":\"chicken\"}"), "\"id\" : \"R101\"");

        run("profile diet vegetarian");

        String result = run("tools call recipe_search {\"query\":\"chicken\"}");
        assertFalse(result.contains("R101"));
        assertContains(result, "\"excludedForSafety\" : 1");
    }

    @Test
    void toolsCallErrors() {
        assertContains(run("tools call recipe_search {not json"), "Error: arguments must be a JSON object");
        assertContains(run("tools call recipe_search [1, 2]"), "Error: arguments must be a JSON object");
        assertContains(run("tools call nope"),
                "Error: Unknown tool 'nope'. Available tools: check_safety, pantry_query, plan_show, recipe_search, shopping_list");
        assertContains(run("tools call check_safety"), "Error: Missing required argument 'recipeIds'");
        assertContains(run("tools frobnicate"), "Usage: tools list | tools schema | tools call <name> [json]");
        assertContains(run("tools"), "Usage: tools list");
        assertContains(run("tools call"), "Usage: tools list");
    }

    @Test
    void helpListsToolsCommands() {
        String help = run("help");

        assertContains(help, "tools list");
        assertContains(help, "tools schema");
        assertContains(help, "tools call <name> [json]");
    }

    // ---- AI agent: recipe generate / pending / discard, memory (scripted model, no network) ----

    /** A scripted model: answers with queued replies (or fails) and records every request. */
    private static final class ScriptedAiClient implements LLMClient {
        final List<LlmRequest> requests = new ArrayList<>();
        final ArrayDeque<String> replies = new ArrayDeque<>();
        LlmException failure;

        ScriptedAiClient reply(String... contents) {
            replies.addAll(List.of(contents));
            return this;
        }

        @Override
        public String providerName() {
            return "ScriptedAI";
        }

        @Override
        public String modelName() {
            return "scripted-model";
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return new LlmResponse(replies.remove(), new TokenUsage(300, 120), "scripted-model");
        }
    }

    /** A recipe reply whose ingredients are 100 g with 20 g protein, 50 g carbs, 10 g fat, 370 kcal and $1.50. */
    private static String recipeReply(String name, String... ingredients) {
        StringBuilder list = new StringBuilder();
        for (String ingredient : ingredients) {
            list.append(list.length() == 0 ? "" : ", ").append("{\"name\": \"").append(ingredient)
                    .append("\", \"quantity\": 100, \"unit\": \"g\", \"calories\": 370, \"proteinG\": 20, ")
                    .append("\"carbsG\": 50, \"fatG\": 10, \"cost\": 1.5}");
        }
        return "{\"recipe\": {\"name\": \"" + name + "\", \"servings\": 2, \"prepMinutes\": 25, \"ingredients\": ["
                + list + "], \"instructions\": [\"Boil the pasta.\", \"Toss with the sauce.\"]}}";
    }

    private ScriptedAiClient useScriptedAi() {
        ScriptedAiClient client = new ScriptedAiClient();
        ctx.llm().useClient(client);
        return client;
    }

    @Test
    void recipeGeneratePrintsActivityPreviewSafetyAndStats() {
        useScriptedAi().reply(recipeReply("Chickpea Pasta", "Chickpea pasta", "Spinach"));

        String output = run("recipe generate high protein vegan pasta");

        assertContains(output, "[agent] Asking the model (call 1)");
        assertContains(output, "[agent] Safety check passed");
        assertContains(output, "Generated recipe AI001: Chickpea Pasta");
        assertContains(output, "Servings: 2 | Prep: 25 min");
        assertContains(output, "Per serving: 370 kcal, 20.0 g protein, 50.0 g carbs, 10.0 g fat, $1.50");
        assertContains(output, "  - 100 g Chickpea pasta");
        assertContains(output, "  1. Boil the pasta.");
        assertContains(output, "Nutrition and cost values are AI estimates.");
        assertContains(output, "Agent: 1 model calls, 0 tool calls, 0 repairs, 0 safety retries; tokens: 300 prompt + 120 completion");
        assertContains(output, "Safety check: passed");
        assertTrue(output.strip().endsWith("Pending recipe: AI001 (not saved)."), output);
    }

    @Test
    void unsafeGenerationPrintsSafetyWarning() {
        run("profile diet vegetarian");
        ScriptedAiClient client = useScriptedAi();
        for (int i = 0; i < 4; i++) {
            client.reply(recipeReply("Chicken Pasta", "Chicken breast", "Pasta"));
        }

        String output = run("recipe generate chicken pasta");

        assertContains(output, "SAFETY WARNING: this recipe could not be made safe for your profile after 4 attempts. It must not be used:");
        assertContains(output, "    Chicken breast is not allowed for a vegetarian diet");
        assertContains(output, "Pending recipe AI001 is flagged unsafe.");
        assertContains(run("recipe pending"), "SAFETY WARNING");
    }

    @Test
    void recipeGenerateErrors() {
        assertContains(run("recipe generate"), "Usage: recipe generate \"<what you want to eat>\"");
        assertContains(run("recipe generate pasta"), "Error: AI features are disabled: Not configured");

        ScriptedAiClient client = useScriptedAi();
        client.failure = new LlmException(LlmErrorKind.SERVER_ERROR, "The AI service had an internal error (HTTP 500)", null);
        String output = run("recipe generate pasta");
        assertContains(output, "Error: The AI service had an internal error (HTTP 500)");
        assertFalse(output.contains("at com.mealmind"), output);
        assertContains(run("recipe pending"), "No pending recipe.");
    }

    @Test
    void pendingDiscardAndReplacement() {
        assertContains(run("recipe pending"), "No pending recipe.");
        assertContains(run("recipe discard"), "No pending recipe.");
        useScriptedAi().reply(recipeReply("First Pasta", "Pasta", "Tomato sauce"), recipeReply("Second Pasta", "Pasta", "Pesto"));

        run("recipe generate first");
        assertContains(run("recipe pending"), "Generated recipe AI001: First Pasta");
        assertContains(run("recipe generate second"), "Generated recipe AI001: Second Pasta");
        assertContains(run("recipe pending"), "Second Pasta");

        assertContains(run("recipe discard"), "Discarded pending recipe AI001.");
        assertContains(run("recipe pending"), "No pending recipe.");
    }

    @Test
    void generatingChangesNoStoredData() {
        useScriptedAi().reply(recipeReply("Bowl", "Rice", "Beans"));

        run("recipe generate a bowl");

        assertEquals(10, run("recipe list").lines().count());
        assertContains(run("recipe show AI001"), "Error: No recipe with id 'AI001'");
        assertTrue(ctx.pantry().isEmpty());
        assertEquals(0, ctx.history().undoSize());
    }

    @Test
    void memoryCommands() {
        assertContains(run("memory show"), "No remembered preferences.");
        assertContains(run("memory remember \"I dislike   cilantro\""), "Remembered: I dislike cilantro");
        assertContains(run("memory remember \"i DISLIKE cilantro\""), "Already remembered.");
        String shown = run("memory show");
        assertContains(shown, "1. I dislike cilantro");
        assertContains(shown, "Conversation messages: 0");
        assertContains(run("memory forget \"I DISLIKE CILANTRO\""), "Forgot: I DISLIKE CILANTRO");
        assertContains(run("memory forget \"never said\""), "Not found.");
        assertContains(run("memory remember \" \""), "Error: A fact must not be blank");
        run("memory remember \"Cooks for two\"");
        assertContains(run("memory clear"), "Cleared remembered preferences and conversation.");
        assertContains(run("memory show"), "No remembered preferences.");
        assertContains(run("memory dump"), "Usage: memory show | memory remember \"<fact>\" | memory forget \"<fact>\" | memory clear");
    }

    @Test
    void rememberedFactsReachTheNextSystemPrompt() {
        ScriptedAiClient client = useScriptedAi().reply(recipeReply("Bowl", "Rice", "Beans"));
        run("memory remember \"Prefers quick breakfasts\"");

        run("recipe generate breakfast");

        assertContains(client.requests.get(0).messages().get(0).content(), "- Prefers quick breakfasts");
        assertContains(run("memory show"), "Conversation messages: 2");
    }

    @Test
    void helpListsAgentCommands() {
        String help = run("help");

        for (String command : new String[] {"recipe generate", "recipe pending", "recipe discard", "memory show",
                "memory remember", "memory forget", "memory clear"}) {
            assertContains(help, command);
        }
        assertContains(run("recipe"), "Usage: recipe list|show|scale|generate|pending|discard");
    }
}
