# MealMind

MealMind Intelligent Meal Planning and Kitchen Management System for EECS 3311 (Software Design), Fall 2026.

**Student**: Abhishek Ramessur (219433564)

## Stages
- **Stage 1 (Design)** - [`stage1/Stage1_Design_Report.md`](Stage1/Stage1_Design_Report.md)
- **Stage 2 (Implementation)** - [`Stage2/Stage2_Implementation_Report.md`](Stage2/Stage2_Implementation_Report.md); setup in [Stage 2: Setup and Configuration](#stage-2-setup-and-configuration)
- **Stage 3 (Testing)** -

## Stage 1 Deliverables
* **Use Case Specifications & Diagrams:** Complete documentation of core user interactions and system boundaries (`UC01` – `UC12`).
* **Class Diagram:** Static structural model outlining controllers, models, agents, tools, and repositories.
* **System Sequence Diagrams:** Dynamic message-passing diagrams (`SD-01` – `SD-11`) mapping out core runtime workflows.
* **Feature-to-Design Traceability Matrix:** Comprehensive mapping linking all system features (`F01` – `F12`) directly to implementing classes, key methods, sequence diagrams, and design patterns.

## Stage 2: Setup and Configuration

### Prerequisites
* JDK 21
* Maven 3.9 or newer
* An OpenAI API key, **or** a local [Ollama](https://ollama.com) install (no key needed)

### 1. Create your local `.env`
The repository contains only `.env.example`, which holds placeholders. Your real settings go in `.env`, which is gitignored.

macOS / Linux / Git Bash:
```bash
cp .env.example .env
```

Windows (Command Prompt):
```bat
copy .env.example .env
```

### 2. Enter your API key
Open `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your own OpenAI key on the `LLM_API_KEY` line. Real environment variables override the values in `.env`. If the provider is `openai` and the key is missing or still the placeholder, MealMind stops with a message saying what to fix.

| Key | Default | Meaning |
|---|---|---|
| `LLM_PROVIDER` | `openai` | `openai` or `ollama` (case-insensitive) |
| `LLM_API_KEY` | _(none)_ | OpenAI API key; required only for `openai` |
| `LLM_MODEL` | `gpt-4o` for openai, `llama3` for ollama | Optional model name sent to the provider |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama server |
| `LLM_TIMEOUT_SECONDS` | `30` | LLM request timeout (positive integer) |

### Using Ollama instead (no key)
Install Ollama, pull a model (for example `ollama pull llama3`), then in `.env` set:
```
LLM_PROVIDER=ollama
```
The model defaults to `llama3`. `LLM_API_KEY` can be left as the placeholder or removed.

### 3. Build and run the tests
```bash
mvn test
```

### 4. Install the pre-commit secret scan (once per clone)
```bash
sh scripts/install-hooks.sh
```
This sets `core.hooksPath` to `scripts/hooks`. The hook blocks any commit that stages `.env`, `.env.*` (except `.env.example`) or `config.local.json`, or adds something that looks like an API key.

### Running the application
* CLI: see [Running the CLI](#running-the-cli) below.
* GUI: _added in a later commit_

### Running the CLI
Start the interactive command line from the project root:
```bash
mvn -q exec:java
```
Your data is saved automatically in the `data/` directory (gitignored): `pantry.json`, `profile.json`, `user-recipes.json` (recipes you saved) and `ratings.json` (ratings and favorites). To keep data somewhere else, set `MEALMIND_DATA_DIR`:
```bash
MEALMIND_DATA_DIR=/path/to/my-data mvn -q exec:java
```
The meal plan is kept in memory for the session only. The ten sample recipes (R101–R110) are always available and always reload at their original servings; they are demo data with approximate nutrition and prices.

| Command | Example |
|---|---|
| `pantry add "<name>" <qty> <unit> [expiry yyyy-MM-dd]` | `pantry add "Chicken Breast" 500 g 2026-11-10` |
| `pantry list` | `pantry list` |
| `pantry remove "<name>"` | `pantry remove "Chicken Breast"` |
| `recipe list [--favorites]` | `recipe list --favorites` |
| `recipe show <id>` | `recipe show R101` |
| `recipe rate --id <id> --stars <1-5\|none>` | `recipe rate --id R103 --stars 5` |
| `recipe favorite --id <id>` | `recipe favorite --id R103` |
| `recipe unfavorite --id <id>` | `recipe unfavorite --id R103` |
| `recipe remove <id>` | `recipe remove AI001` (saved recipes only) |
| `recipe scale --id <id> --servings <n>` | `recipe scale --id R101 --servings 4` |
| `recipe recommend [--pantry-first] [--max-time n] [--limit n] [--explain]` | `recipe recommend --pantry-first --max-time 30` |
| `plan new --days <n>` | `plan new --days 5` |
| `plan set <day> <slot> <recipeId> [--servings <n>]` | `plan set 1 dinner R101 --servings 3` |
| `plan clear <day> <slot>` | `plan clear 1 dinner` |
| `plan show` | `plan show` |
| `plan check` | `plan check` |
| `plan generate [--days n] [--strategy id] [--slots lunch,dinner] [--budget n] [--replace]` | `plan generate --days 5 --strategy budget --budget 60` |
| `plan strategies` | `plan strategies` |
| `plan nutrition [--explain]` | `plan nutrition` |
| `plan edit "<what to change>"` | `plan edit "Swap Tuesday dinner for a 15-minute quick meal"` |
| `plan undo` / `plan redo` | `plan undo` (same as `undo` / `redo`) |
| `profile show` | `profile show` |
| `profile diet <omnivore\|vegetarian\|vegan\|pescatarian>` | `profile diet vegetarian` |
| `profile allergy add\|remove "<name>"` | `profile allergy add "tree nuts"` |
| `profile update [--calories n] [--protein g] [--carbs g] [--fat g] [--budget n] [--servings n]` | `profile update --calories 2000 --budget 100` (use `none` to clear a value) |
| `shopping list` | `shopping list` |
| `shopping export --format txt [--file <path>]` | `shopping export --format txt --file shopping.txt` |
| `undo` / `redo` | `undo` |
| `help` | `help` |
| `exit` / `quit` | `exit` |

Units: `g`, `kg`, `ml`, `l`, `tsp`, `tbsp`, `cup`, `pc` (full names such as `grams` also work). Slots: `breakfast`, `lunch`, `dinner`, `snack`.

Example session:
```
mealmind> pantry add Rice 500 g
Added Rice. Pantry now has 500 g.
mealmind> pantry add "Chicken breast" 200 g 2026-11-02
Added Chicken breast. Pantry now has 200 g.
mealmind> recipe scale --id R101 --servings 4
Scaled Chicken Stir-Fry to 4 servings (per-serving values unchanged).
mealmind> plan set 1 dinner R101
Day 1 Dinner: Chicken Stir-Fry (4 servings)
mealmind> shopping list
MealMind Shopping List
...
Meat & Seafood:
- 400 g Chicken breast (~$6.00)
...
mealmind> undo
Undone: Replace Day 1 DINNER with Chicken Stir-Fry
mealmind> exit
```
(Lines starting with `[update]` are printed whenever the shopping list changes.)

#### Dietary profile and safety checks
Set your diet, allergens and optional daily targets with the `profile` commands. Before `plan set` adds a meal, MealMind checks it against your profile: a meal containing a listed allergen or a food your diet excludes is **refused** and the plan is left unchanged. Calorie, macro and budget targets only produce warnings. `plan check` reviews the whole plan, and `recipe show` ends with a profile check line.
```
mealmind> profile diet vegetarian
Diet set to Vegetarian.
mealmind> profile allergy add peanut
Allergen added: peanut
mealmind> plan set 1 dinner R101
Refused: Chicken breast is not allowed for a vegetarian diet (contains chicken)
mealmind> plan set 1 dinner R103
Day 1 Dinner: Red Lentil Soup (4 servings)
mealmind> plan check
Plan is safe and within your targets.
```
The safety check is a conservative **keyword heuristic on ingredient names**. It cannot see hidden ingredients (for example dairy inside a sauce) and is not medical advice. Profile changes are settings and are not undoable with `undo`. Describing your diet in free text (for example `profile set --text "..."`) arrives with the AI commands in a later commit.

The AI agent commands (recipe generation, plan generation, natural-language edits) arrive in a later commit, and the JavaFX GUI after that.

#### Tool layer
The AI agent (added in the next commit) will see the app only through five **read-only** tools. You can call them yourself with `tools list`, `tools schema` and `tools call <name> [json]`, with no AI configured. Everything after the tool name is the JSON arguments, typed as-is.

| Tool | Arguments | Example |
|---|---|---|
| `pantry_query` | `ingredient` or `expiringWithinDays` (0–60), both optional | `tools call pantry_query {"expiringWithinDays": 3}` |
| `recipe_search` | `id`, or any of `query`, `maxPrepMinutes`, `maxCaloriesPerServing`, `pantryFirst`, `limit` (1–10) | `tools call recipe_search {"query": "lentil", "pantryFirst": true}` |
| `check_safety` | `recipeIds` (1–10 ids, required) | `tools call check_safety {"recipeIds": ["R101", "R103"]}` |
| `plan_show` | none | `tools call plan_show` |
| `shopping_list` | none | `tools call shopping_list` |

Tools never change your data. `recipe_search` never shows a recipe that conflicts with your diet or allergens; it only reports how many were hidden (`excludedForSafety`). `check_safety` explains why a recipe is not allowed.

#### AI agent: recipe generation
`recipe generate` asks the AI agent for a new recipe. The agent may look things up with the read-only tools above (for example your pantry or existing recipes) and then drafts a recipe. MealMind's own deterministic checker then validates the draft against your diet and allergens. An unsafe draft is sent back to the model for correction, up to 3 times. If the agent still cannot produce a safe recipe, the result is shown only as a warning. The agent's steps are printed live as `[agent] …` lines.

| Command | Example |
|---|---|
| `recipe generate <what you want>` | `recipe generate high protein vegan pasta for two` |
| `recipe pending` | `recipe pending` (shows the last generated recipe and its safety status) |
| `recipe save` | `recipe save` (keeps the pending recipe; `undo` removes it again) |
| `recipe discard` | `recipe discard` |
| `memory show` | `memory show` (preferences the agent will take into account) |
| `memory remember "<fact>"` | `memory remember "I dislike cilantro"` |
| `memory forget "<fact>"` | `memory forget "I dislike cilantro"` |
| `memory clear` | `memory clear` |

Limitations:
- Nutrition and cost values in generated recipes are **AI estimates**, checked only for plausibility (calories must roughly match the protein, carbs and fat). There is no nutrition database.
- The safety check is a keyword heuristic on ingredient names. It cannot see hidden ingredients and is **not medical advice**.
- A generated recipe is never saved automatically. `recipe save` keeps it only if it passes the safety check again against your **current** profile; an unsafe recipe can never be saved.
- Results depend on the model you configure.
- Each generation makes several paid API calls. Use an API key with a low spending limit, and check the token totals with `llm status`.

#### Ratings, favorites and saved recipes
Rate any recipe from 1 to 5 (`recipe rate --id R103 --stars 5`, or `--stars none` to clear) and mark favorites (`recipe favorite --id R103`). `recipe list` shows each rating as `4/5` (or `-`) and `fav` at the end of the line, `recipe list --favorites` shows only favorites, and `recipe show` prints your rating. Ratings are saved in `ratings.json`.

To keep an AI recipe: `recipe generate ...`, check it with `recipe pending`, then `recipe save`. It gets an id such as `AI001`, is written to `user-recipes.json`, and can be rated like any other recipe. `undo` right after saving removes it again; `recipe remove AI001` deletes a saved recipe later (the bundled samples cannot be removed).

Your ratings also steer the AI: the next `recipe generate` prompt includes the **names** of recipes you rated highly or poorly and your favorites, labeled as data and used only as a taste hint (see them with `memory show`). The `recipe_search` tool reports each recipe's `rating` and `favorite` flag.

Limitations:
- Removing a recipe drops its rating and favorite flag, and `undo` brings the recipe back but **not** its rating.
- A saved AI recipe keeps its AI-estimated nutrition and cost.
- Scaling a saved recipe (`recipe scale`) changes the saved copy permanently; bundled recipes always reload at their original servings.

#### Rule-based plan generation
`plan generate` fills a whole plan from the recipe book in one step. It is deterministic, works offline and uses **no AI** (it is the Stage 1 "local rule-based fallback generator"). `plan strategies` lists the strategies:
- `pantry-first` (default): prefers recipes the pantry already covers, then fewer missing ingredients.
- `budget`: prefers the lowest cost per serving.
- `high-protein`: prefers the most protein per serving.

Defaults: 7 days, `pantry-first`, lunch and dinner, no budget. Only recipes that are safe for your profile are used; recipes you rated 1-2 stars are skipped while others are available; each recipe is used once before any repeats; ties go to favorites, then higher ratings. Meals are scaled to your household servings. `--budget` is for the whole plan and only adds a note.
```
mealmind> plan generate --days 3 --strategy high-protein --slots lunch,dinner --budget 40
Generated 3-day plan using High-Protein (rule-based, no AI)
Strategy: High-Protein (rule-based, no AI)
Plan cost $... is within your budget of $40.00
Use 'plan show' or 'shopping list'.
```
If the current plan already has meals, add `--replace`. Generating replaces the whole plan and clears the undo history, so it **cannot be undone**; edits made afterwards with `plan set` / `plan clear` can.

Limitations:
- Recipes have no meal-type tags, so a breakfast slot may get any safe recipe (for example a soup).
- Pantry use is not simulated across meals: every recipe is scored against the pantry as it is now.
- The AI planner (free-text requests) comes in a later commit.

#### Nutrition analysis
`plan nutrition` adds up the current plan: one line per day that has meals, the plan totals, the daily average (over the days that have meals) and, for each calorie or macro target set in your profile, how close the average comes. All numbers are computed by MealMind from the recipes; no AI is involved.
```
mealmind> plan nutrition
Nutrition summary
Day 1: 2 meals, 842 kcal, P 37.9 g, C 103.0 g, F 34.2 g, $3.77
Totals (2 meals planned, 28 slots): 842 kcal, P 37.9 g, C 103.0 g, F 34.2 g, $3.77
Daily average (1 day with meals): 842 kcal, P 37.9 g, C 103.0 g, F 34.2 g, $3.77
Calories: 842 of 2000 kcal per day (42%)
```
`plan nutrition --explain` prints the same figures and then a short **AI commentary** on them. The AI only interprets the numbers it is given and never changes them.

#### Recipe recommendations
`recipe recommend` lists up to 5 recipes that are safe for your profile, ranked by your favorites and ratings first, then by how much of each recipe your pantry already covers. `--pantry-first` ranks by pantry coverage first, `--max-time 30` keeps recipes that take at most 30 minutes, and `--limit n` (1-10) changes how many are shown. Recipes rated 1-2 stars are left out while others are available. If nothing fits the time limit, the limit is relaxed and the quickest recipes are shown with a note; with an empty pantry the ranking uses only your ratings and favorites. Safety rules are never relaxed.
```
mealmind> recipe recommend --pantry-first --limit 2
1. R103 Red Lentil Soup - Pantry covers 13% of the recipe; missing: Onion, Carrots, Garlic and 4 more; 40 min
2. R109 Peanut Butter Banana Toast - Pantry covers 0% of the recipe; missing: Whole wheat bread, Peanut butter, Banana and 1 more; 5 min
```
`recipe recommend --explain` prints the same list and then an **AI-ranked** version: the AI may only reorder those recipes and write a short reason for each; it cannot add a recipe.

**What is computed and what is AI.** Numbers, filtering and the first ranking always come from code and work offline. `--explain` is an explicit opt-in because it calls the configured AI provider, which can cost a few cents per request. If AI is not configured or the call fails, the deterministic result is still printed, followed by one line `AI explanation unavailable: <reason>`.

Limitations:
- The nutrition values of AI-generated recipes are AI estimates, so their totals are estimates too.
- An ingredient whose calories and macros are all 0 is flagged as "Nutritional data unavailable" unless its name is on a short list of genuinely zero-nutrition items (water, ice, salt, pepper, baking soda, baking powder, vinegar, spices, seasonings).
- Safety keywords and pantry matching are name-based heuristics: they cannot see hidden ingredients, and a pantry item only counts when its name and unit match the recipe.

#### Natural-language plan editing
`plan edit` changes **one** meal in the current plan from a plain-English request (quotes are optional):
```
mealmind> plan edit Swap Tuesday dinner for a 15-minute quick meal
mealmind> plan edit clear Monday lunch
mealmind> plan edit use my favorite recipe for Wednesday breakfast, 3 servings
```
The AI only **interprets** the request: it may look things up with the read-only tools (for example `recipe_search` with a time limit to find a 15-minute meal) and then proposes one edit. MealMind checks that proposal itself: the recipe must exist in the recipe book and pass the diet and allergen check against your **current** profile, both when the AI proposes it and again right before it is applied. An unsafe or invalid choice is never applied: you see `Refused: ...` and `Plan unchanged.` Every applied edit goes through the undo history, so `undo` (or `plan undo`) reverts it and `redo` (or `plan redo`) reapplies it. Calorie and budget warnings are shown as `Warning: ...` but do not block.

If the request is ambiguous ("change dinner") or asks for several changes at once, MealMind asks a clarifying question instead and changes nothing; answer with another `plan edit` command. Follow-up edits ("make that one vegetarian") use the conversation memory (`memory show`). Plans have day numbers, not dates: **Day 1 is Monday**, Day 2 is Tuesday, and so on.

`plan edit` needs AI configured. Each edit can make several paid model calls, so use a provider account with a low spend limit. Without AI, or if the call fails, it prints one `Error: ...` line and the plan is not changed.

Limitations:
- One edit per request.
- The AI may misread a request; that is why every change is undoable.
- The safety check is keyword-based: it cannot see hidden ingredients and is not medical advice.

### AI configuration
MealMind works without any AI provider: pantry, recipes, scaling, plans, profile checks and the shopping list are all deterministic. If AI is not configured, the CLI prints one line at startup ("AI features are disabled: …") and carries on. Startup never contacts the network.

**OpenAI (primary, default model `gpt-4o`)**
1. `cp .env.example .env` (Windows: `copy .env.example .env`).
2. In `.env`, replace `YOUR_LOCAL_API_KEY_HERE` with your own key on the `LLM_API_KEY` line.
3. Optionally uncomment `LLM_MODEL` to choose another OpenAI model.

**Ollama (local, no key needed, default model `llama3`)**
1. Install Ollama from <https://ollama.com> and run `ollama pull llama3`.
2. In `.env`, set `LLM_PROVIDER=ollama`. The model defaults to `llama3`; uncomment `LLM_MODEL` only to use a different Ollama model.
3. `OLLAMA_BASE_URL` defaults to `http://localhost:11434`.

**Checking the setup from the CLI**
| Command | What it does |
|---|---|
| `llm status` | Shows the provider, model, whether AI is available (or why not) and the calls and tokens used this session |
| `llm ping` | Sends **one tiny real request** ("Reply with the single word OK.", at most 8 tokens) and prints the reply and token counts |

Failed AI calls print a one-line reason, such as "Could not reach Ollama at http://localhost:11434. Is it running?" or "The AI service rejected the API key (HTTP 401)". Transient failures are retried once automatically. The AI recipe, plan and editing commands arrive in later commits.

**Never show `.env` in screenshots, screen recordings or demo videos**, and never paste a key into chat, reports or issues.

### Security
* Never commit `.env`. Run `git status` before committing.
* Never paste API keys into reports, screenshots, or demo videos.
* If a key is ever exposed, rotate (revoke and regenerate) it in the provider's dashboard right away. Deleting it from Git is not enough.
