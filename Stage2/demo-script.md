# MealMind Stage 2 - Demo recording script

This script plans the demonstration videos for every implemented feature (F-IDs from the Stage 1 report, N-IDs for new features in `stage2-report.md`, Section 2.2). Together the videos must show **both** the JavaFX GUI and the CLI. Every video starts by naming the feature IDs and names it covers, and every segment below should be introduced the same way. The recordings are **eight videos, V1 to V8** (links below); each segment's "Timestamp" line gives where it starts, as plain text "V3 @ 3:22", and Section 7 of the report repeats the links and timestamps.

All commands below were checked against `help` and run on the development machine; expected outputs were copied from real sessions (numbers can differ slightly if the data differ).

---

## Pre-recording checklist

1. **Launch commands.**
   - GUI (simplest): `mvn -q javafx:run`
   - CLI, bash or Git Bash: `mvn -q exec:java`; GUI through exec: `mvn -q exec:java -Dexec.args="--gui"`
   - CLI options in **PowerShell** need the whole property quoted: `mvn -q exec:java "-Dexec.args=--gui"`, `mvn -q exec:java "-Dexec.args=--gui-selftest"`
2. **Never show secrets.** Do not open `.env`, do not print environment variables, do not show the terminal history that set them. Put the key into `.env` *before* recording, or use Ollama (`LLM_PROVIDER=ollama`) so that no key exists at all. `llm status` is safe to show: it prints only the provider, the model and counters.
3. **Clean, reproducible data.** Start every video from an empty temporary data folder:
   - bash: `export MEALMIND_DATA_DIR="$(mktemp -d)"`
   - PowerShell: `$env:MEALMIND_DATA_DIR = Join-Path $env:TEMP "mealmind-demo"; Remove-Item -Recurse -Force $env:MEALMIND_DATA_DIR -ErrorAction SilentlyContinue`
   Delete it after recording. Never record with your real `data/` folder.
4. **AI features need the real provider configured** (OpenAI key in `.env`, or a running Ollama with the model pulled). Check with `llm status` and `llm ping` before recording. Flows marked **needs a real model** below fail with a clear "Error: ..." or "AI explanation unavailable: ..." line otherwise; everything else works offline.
5. **Video links must open without an access request**: use unlisted YouTube, or Google Drive / OneDrive shared as "anyone with the link can view". Test each link in a private browser window.
6. Close other applications and notifications; use a readable terminal font size (at least 16 pt) and a window size of at least 1280 x 720.

---

## Proposed video segmentation (recommended order)

| Video | Features | Interface | Needs a real model | Estimated length | Spoken intro |
|---|---|---|---|---|---|
| V1 | N08, N02, N07 | CLI + GUI | no (ping optional) | 4 min | "Video 1 shows N08 secure configuration and local persistence, N02 AI provider status, and N07 the GUI shell and its self-test." |
| V2 | F01, F02, N01 | CLI + GUI | no | 5 min | "Video 2 shows F01 dietary profile management, F02 pantry inventory, and N01 undo and redo." |
| V3 | F08, F12, F03 (deterministic), N05 (removal) | CLI + GUI | no | 5 min | "Video 3 shows F08 recipe scaling, F12 ratings and favourites, and F03 pantry recipe recommendations without AI." |
| V4 | F04, N05, N03, N04 | CLI + GUI | **yes** | 6 min | "Video 4 shows F04 AI recipe generation with the safety gate, N05 saving and removing AI recipes, N03 agent memory, and N04 the agent's tools." |
| V5 | F05, N06, F11 | CLI + GUI | no | 6 min | "Video 5 shows F05 rule-based weekly plan generation, N06 manual plan building and checks, and F11 the shopping list with export." |
| V6 | F06, F09, F03 (AI part) | CLI + GUI | **yes** | 6 min | "Video 6 shows F06 natural-language plan editing, F09 nutrition analysis with AI commentary, and the AI re-ranking part of F03." |
| V7 | F10, F07 | CLI | F07 **yes**; F10 no (`--ai` optional) | see the video | "Video 7 shows F10 expiring-leftover planning and F07 AI ingredient substitution in the CLI." |
| V8 | F10, F07 | GUI | F07 **yes**; F10 no (AI re-ranking optional) | see the video | "Video 8 shows F10 expiring-leftover planning on the Pantry tab and F07 AI ingredient substitution on the Recipes tab." |

Record V1 to V3, V5 and the F10 parts of V7 and V8 offline first; record V4, V6 and the F07 parts of V7 and V8 with the provider configured. All AI segments were recorded with Ollama llama3.

### Video links

| Video | Link (Google Drive, "Anyone with the link" can view) |
|---|---|
| V1 | [V1](https://drive.google.com/file/d/1WrPqMlVCI6N-rvI42sCBM7YL5X5mfLP2/view?usp=drive_link) |
| V2 | [V2](https://drive.google.com/file/d/1vet0Gj_fU2nsUves0OMEFiCDyo4JhxEq/view?usp=drive_link) |
| V3 | [V3](https://drive.google.com/file/d/1cPS0_xjkevuDOwuuOOpc3X1hbSNesPK8/view?usp=drive_link) |
| V4 | [V4](https://drive.google.com/file/d/1wjihwLMlI6hYPTo9zjNEM4gFv_4gmCVU/view?usp=drive_link) |
| V5 | [V5](https://drive.google.com/file/d/1UkWexhrrO7uHwRXsO4fNqd-fsKfySoEJ/view?usp=drive_link) |
| V6 | [V6](https://drive.google.com/file/d/1BxB-bvwUx4daLkAlgmjz-uCCWZoCaE5o/view?usp=drive_link) |
| V7 | [V7](https://drive.google.com/file/d/1vhn0A1UamYrvtggfaNarqjJjC_7_mUMM/view?usp=drive_link) |
| V8 | [V8](https://drive.google.com/file/d/1o0cyJ2548WVAT9vM5_04Qck9pRLzUHK9/view?usp=drive_link) |


---

## N08 Secure configuration and local persistence (V1)

- **Interface:** CLI (terminal) and the file explorer (without opening `.env`).
- **Steps:** show `.env.example` in the editor (placeholders only); show that `.env` is listed in `.gitignore`; run `git ls-files | grep env` (bash) to show only `.env.example` is tracked. Start the CLI with a fresh `MEALMIND_DATA_DIR`, run `pantry add Rice 500 g`, `exit`, start again and run `pantry list`.
- **Expected output:** `Added Rice. Pantry now has 500 g.`, then after the restart `Rice  500 g  -`.
- **AI:** none. Say: "The key is read only from .env or the environment and is never printed; a pre-commit hook blocks key-like text."
- **Error case:** start with a corrupt pantry file (write `not json` into `pantry.json` in the temp folder): the program prints `Error: ...` naming the file and exits with code 1 without overwriting it.
- **Timestamp:** V1 @ 0:12 (CLI)

## N02 AI provider status and connectivity check (V1)

- **Interface:** CLI, then the GUI toolbar.
- **Steps:** `llm status`, then `llm ping` (if a provider is configured). In the GUI, point at the AI status label on the right of the toolbar.
- **Expected output:** `Provider: Ollama`, `Model: llama3`, `Status: available`, `AI calls: 0 ok, 0 failed; tokens: ...` (all recordings used Ollama llama3; OpenAI was not used). Without configuration the welcome line reads `AI features are disabled: ... Deterministic features still work.` and the GUI label reads `AI disabled: <reason>`.
- **AI:** only `llm ping` calls the model (one tiny request).
- **Error case:** with no key and `LLM_PROVIDER=openai`, show the disabled message and that pantry commands still work.
- **Timestamp:** V1 @ 2:47 (CLI); V1 @ 5:12-5:20 (GUI)

## N07 GUI shell, launch options and GUI self-test (V1)

- **Interface:** GUI and terminal.
- **Steps:** `mvn -q javafx:run` (window "MealMind", tabs Profile, Pantry, Recipes, Plan, Shopping List, Nutrition; Undo/Redo toolbar; status bar). Close it. Run `mvn -q exec:java "-Dexec.args=--gui-selftest"` (PowerShell) or `mvn -q exec:java -Dexec.args="--gui-selftest"` (bash).
- **Expected output:** 36 lines starting `ok:` and finally `GUI self-test: ok` (exit code 0).
- **AI:** none.
- **Alternative case:** on a machine without a display it prints `GUI self-test skipped: ...` and exits with code 3.
- **Timestamp:** V1 @ 4:40 (GUI)

## F01 Dietary profile management (deterministic part) (V2)

- **Interface:** CLI, then GUI Profile tab.
- **CLI input:** `profile diet vegetarian`, `profile allergy add peanut`, `profile update --calories 2000 --protein 90`, `profile show`.
- **Expected output:** `Diet set to Vegetarian.`, `Allergen added: peanut`, `Profile updated: calories 2000, protein 90`, then the profile listing (`Calorie target: 2000 kcal/day`, `Protein target: 90 g/day`, ...).
- **GUI:** Profile tab: change Diet, add an allergen, type a target, press **Apply**; press **Reset** to reload.
- **AI:** none (free-text profile parsing from Stage 1 is not implemented; the hint label says so).
- **Error case:** `profile update --calories 100` prints `Error: Calorie target must be between 800 and 6000 kcal, got 100`; in the GUI the same message appears in red next to the field and nothing is saved.
- **Timestamp:** V2 @ 0:12 (CLI); V2 @ 4:10 (GUI)

## F02 Pantry inventory management (V2)

- **Interface:** CLI, then GUI Pantry tab.
- **CLI input:** `pantry add "Red lentils" 400 g 2026-11-10`, `pantry add Onion 3 pc`, `pantry list`, `pantry remove Onion`.
- **Expected output:** `Added Red lentils. Pantry now has 400 g.`; the list shows `Red lentils  400 g  2026-11-10  expires in N days` (choose a date 1-3 days ahead to show the warning).
- **GUI:** add an item with the form (unit drop-down, date picker); expired rows are red and rows expiring within 3 days are amber; **Remove Selected**.
- **AI:** none.
- **Error case:** `pantry add "Red lentils" 1 l` prints `Error: Cannot convert LITER (VOLUME) to GRAM (MASS): units measure different dimensions`; nothing changes.
- **Timestamp:** V2 @ 1:36 (CLI); V2 @ 5:00 (GUI)

## N01 Undo and redo (V2)

- **Interface:** CLI, then GUI toolbar.
- **CLI input:** after a pantry add: `undo`, then `redo`.
- **Expected output:** `Undone: Add Onion to pantry`, `Redone: Add Onion to pantry`.
- **GUI:** the toolbar buttons read `Undo: Add ... to pantry`; press Undo (or Ctrl+Z) and Redo (Ctrl+Y); the status bar shows the same text as the CLI.
- **AI:** none.
- **Alternative case:** `undo` with nothing to undo prints `Nothing to undo.`; explain that profile and rating changes are settings and are not undoable.
- **Timestamp:** V2 @ 2:52 (CLI); V2 @ 6:00 (GUI)

## F08 Recipe and meal scaling (V3)

- **Interface:** CLI, then GUI Recipes tab (Details).
- **CLI input:** `recipe scale --id R103 --servings 6`, then `undo`.
- **Expected output:** `Scaled Red Lentil Soup to 6 servings (per-serving values unchanged).`, the per-serving line and ingredients (`375 g Red lentils`, `1.5 l Vegetable broth`, `1 tsp Salt, to taste` unchanged); then `Undone: Scale Red Lentil Soup to 6 servings`.
- **GUI:** select R103, type 6 in Servings, press **Scale**, then Undo.
- **AI:** none.
- **Error case:** `recipe scale --id R103 --servings 0` prints `Error: Target servings must be at least 1, got 0`.
- **Timestamp:** V3 @ 0:11 (CLI); V3 @ 6:34-6:47 (GUI)

## F12 Ratings, favourites and memory (V3)

- **Interface:** CLI, then GUI Recipes tab.
- **CLI input:** `recipe rate --id R103 --stars 5`, `recipe favorite --id R103`, `recipe list --favorites`, `memory show`.
- **Expected output:** `Rated Red Lentil Soup: 5/5.`, `Added Red Lentil Soup to favorites.`, `R103  Red Lentil Soup ... 5/5  fav`; `memory show` lists "Derived from your ratings: Recipes the user rated highly (4-5 stars): Red Lentil Soup."
- **GUI:** Rating choice plus **Rate**, the **Favorite** toggle, the **Favorites only** filter.
- **AI:** ratings are added to AI prompts as data (taste hints).
- **Error case:** `recipe rate --id R103 --stars 9` prints `Error: Rating must be between 1 and 5`.
- **Timestamp:** V3 @ 1:36 (CLI); V3 @ 5:49-6:33 (GUI)

## F03 Pantry recipe recommendation (deterministic part in V3, AI part in V6)

- **Interface:** CLI, then GUI Recipes tab (Recommendations).
- **CLI input:** `recipe recommend --pantry-first --max-time 30 --limit 3`.
- **Expected output:** numbered lines such as `1. R110 Greek Yogurt Parfait - Pantry covers 0% of the recipe; missing: ...; 5 min` (coverage rises when the pantry holds the ingredients).
- **GUI:** tick Pantry first, set Max minutes and Limit, press **Recommend**; click a row to select that recipe.
- **AI (V6, needs a real model):** `recipe recommend --explain` or tick **Explain with AI**: the list is re-ranked with short AI reasons under "AI-ranked"; say that the AI can only reorder these safe candidates.
- **Alternative case:** `recipe recommend --max-time 1` prints `Note: No recipe fits 1 minutes; showing the closest matches instead.`; with AI unavailable `--explain` prints `AI explanation unavailable: ...` after the deterministic list.
- **Timestamp:** No AI: V3 @ 3:22 (CLI), V3 @ 7:03 (GUI); AI re-ranking: V6 @ 3:28 (CLI), V6 @ 8:42 (GUI)

## F04 AI recipe generation (V4, needs a real model)

- **Interface:** CLI, then GUI Recipes tab (Generate).
- **CLI input:** `recipe generate "high-protein vegetarian pasta under 20 minutes"`, then `recipe pending`.
- **Expected output:** live `[agent] ...` lines (asking the model, tool calls, `Safety check passed`), the preview (`Generated recipe AI001: ...`, servings, per-serving line, ingredients, steps, `Nutrition and cost values are AI estimates.`), `Safety check: passed`, `Pending recipe: AI001 (not saved).` and the agent stats line.
- **GUI:** type the request, press **Generate**; watch the Agent activity log; the preview shows a green "Safety check: passed" banner.
- **AI:** the model writes the recipe and may call read-only tools; code parses it, checks plausibility and safety and retries up to 3 times. Say: "Nothing is saved automatically."
- **Error case:** with a vegetarian profile ask for `recipe generate "chicken stir fry"`: either the model corrects itself after safety feedback, or the candidate is shown with a red `SAFETY WARNING ...` banner and **Save is disabled**.
- **Timestamp:** V4 @ 0:16 (CLI); V4 @ 6:46 (GUI)

## N05 Saved-recipe management (V4; removal also in V3)

- **Interface:** CLI, then GUI.
- **CLI input:** `recipe save`, `recipe list`, `undo`, `recipe generate ...`, `recipe save`, `recipe remove AI001`.
- **Expected output:** `Saved <name> as AI001.`, the recipe appears in `recipe list`; `undo` removes it; `Removed <name>. Its rating and favorite flag were dropped.`
- **GUI:** **Save** / **Discard** in the Generate section; **Remove saved recipe** in Details (with a confirmation dialog).
- **AI:** the save itself is deterministic (re-validated against the current profile).
- **Error case:** `recipe remove R103` prints `Error: R103 is a bundled sample recipe and cannot be removed`; in the GUI the button is disabled for bundled recipes.
- **Timestamp:** V3 @ 4:53 (CLI, removal error); V4 @ 1:41 (CLI, save, undo, redo, remove); V3 @ 6:53 (GUI, greyed-out Remove); V4 @ 7:21 (GUI, save and remove)

## N03 Agent memory commands (V4)

- **Interface:** CLI.
- **CLI input:** `memory remember "prefers quick breakfasts"`, `memory show`, `memory forget "prefers quick breakfasts"`.
- **Expected output:** `Remembered: prefers quick breakfasts`, then `1. prefers quick breakfasts`, `Conversation messages: N` and the rating-derived notes.
- **AI:** remembered facts are sent with every AI request as preference notes.
- **Alternative case:** `memory forget "something unknown"` prints `Not found.`
- **Timestamp:** V4 @ 4:26 (CLI)

## N04 Agent tool inspection (V4)

- **Interface:** CLI.
- **CLI input:** `tools list`, `tools call recipe_search {"query": "lentil"}`, `tools call pantry_query {}`.
- **Expected output:** the five tools (`check_safety`, `pantry_query`, `plan_show`, `recipe_search`, `shopping_list`) with their parameters; the call prints the JSON the agent would see.
- **AI:** none (this shows exactly what the model can read; tools never change data).
- **Error case:** `tools call recipe_search {"limit": 50}` prints an error naming the allowed range.
- **Timestamp:** V4 @ 5:28 (CLI)

## F05 Weekly meal plan generation (rule-based) (V5)

- **Interface:** CLI, then GUI Plan tab.
- **CLI input:** `plan strategies`, `plan generate --days 3 --strategy budget --budget 40`, `plan show`.
- **Expected output:** the three strategies; `Generated 3-day plan using Budget-Conscious (rule-based, no AI)`, `Plan cost $... is within your budget of $40.00`; the plan listing.
- **GUI:** set Days, Strategy, slots and Budget; press **Generate (rule-based)**; the grid fills with day totals.
- **AI:** none. Say: "This is the offline generator from Stage 1; AI plan generation is not implemented."
- **Alternative case:** run `plan generate` again: `Error: the current plan already has meals. Use --replace to overwrite it (this cannot be undone).`; in the GUI a confirmation dialog appears.
- **Timestamp:** V5 @ 0:13 (CLI); V5 @ 7:54 (GUI)

## N06 Manual plan building and plan check (V5)

- **Interface:** CLI, then GUI Plan tab (Edit selected slot).
- **CLI input:** `plan set 1 breakfast R110`, `plan check`, `plan clear 1 breakfast`, `undo`.
- **Expected output:** `Day 1 Breakfast: Greek Yogurt Parfait (2 servings)`, `Plan is safe and within your targets.`, `Cleared Day 1 Breakfast.`
- **GUI:** click a cell, choose a recipe, press **Set meal** or **Clear slot**; Undo restores.
- **AI:** none.
- **Error case (allergen/diet refusal):** with the vegetarian profile, `plan set 1 breakfast R101` prints `Refused: Chicken breast is not allowed for a vegetarian diet (contains chicken)` and nothing changes; the GUI shows the same "Refused:" text.
- **Timestamp:** V5 @ 2:42 (CLI); V5 @ 8:51 (GUI)

## F11 Shopping list (V5)

- **Interface:** CLI, then GUI Shopping List tab.
- **CLI input:** `shopping list`, then `pantry add "Greek yogurt" 1 kg`, `shopping list` again, then `shopping export --format txt --file shopping-list.txt`.
- **Expected output:** `MealMind Shopping List` grouped by aisle (`Produce:`, `Dairy & Eggs:`, ...), each line `- 700 g Greek yogurt (~$4.90)`, `Estimated total: $...`; after the pantry add the item disappears; `Shopping list written to shopping-list.txt`.
- **GUI:** the table updates by itself after a pantry change; tick **Got it** boxes (session only); **Copy as text**; **Export .txt** (dialog suggests `shopping-list.txt`).
- **AI:** none.
- **Alternative case:** an ingredient stored in an incompatible unit stays on the list with a note such as `[Pantry stock of Onion is in a unit that cannot be compared (pc); full amount listed]`.
- **Timestamp:** V5 @ 4:52 (CLI); V5 @ 11:03 (GUI)

## F06 Natural-language plan editing (V6, needs a real model)

- **Interface:** CLI, then GUI Plan tab ("Ask the planner").
- **CLI input:** `plan edit "swap tuesday dinner for a quicker meal"`, then `plan show`, then `plan undo`.
- **Expected output:** `[agent] ...` activity (including a `recipe_search` tool call), `Edit applied: Replaced Day 2 DINNER with ...`, `Use 'undo' to revert.`, the stats line; `plan undo` prints `Undone: Replace Day 2 DINNER with ...`.
- **GUI:** type the request in **Ask the planner**, press **Send**; the grid cell changes; Undo reverts.
- **AI:** the model only interprets the request; code checks the choice twice and applies it as an undoable command. Day 1 is Monday.
- **Alternative cases:** `plan edit "change dinner"` gives `Question: Which day ...?` and `Plan unchanged.`; asking for a meat dish with a vegetarian profile ends in `Refused: ...` and `Plan unchanged.`
- **Timestamp:** V6 @ 0:17 (CLI); V6 @ 6:58 (GUI)

## F09 Nutrition analysis and AI commentary (V6; the deterministic part also works offline)

- **Interface:** CLI, then GUI Nutrition tab.
- **CLI input:** `plan nutrition`, then `plan nutrition --explain`.
- **Expected output:** `Nutrition summary`, one line per day (`Day 1: 3 meals, 1495 kcal, P 76.4 g, ...`), `Totals (...)`, `Daily average (...)`, `Calories: 1101 of 2000 kcal per day (55%)`; with `--explain` an extra line `AI commentary: ...`.
- **GUI:** day lines, progress bars per target (green under 110%, red from 110%), **Explain with AI** with its activity log.
- **AI:** the commentary only interprets the computed numbers; it never produces numbers.
- **Alternative case:** without AI, `--explain` prints the numbers and then `AI explanation unavailable: AI features are disabled: ...`; with an empty plan: `No meals planned; nothing to analyze.`
- **Timestamp:** V6 @ 2:41 (CLI); V6 @ 9:16 (GUI)

**End of V6.** F07 and F10 are demonstrated in V7 (CLI) and V8 (GUI).

## F10 Expiring leftover utilization (V7 CLI, V8 GUI; works offline)

- **Interface:** CLI, then the GUI Pantry tab's "Use up expiring items" section (an inline section instead of Stage 1's "Clear Expiring Items" modal; say so).
- **CLI input:** `pantry add "Red lentils" 400 g <tomorrow yyyy-MM-dd>`, `pantry add Spinach 200 g <tomorrow>`, `plan generate --days 3`, `plan leftovers`, `plan show`, `plan leftovers apply`, `plan show`, `undo`.
- **Expected output:** `Expiring within 3 days: Red lentils (expires in 1 day); Spinach (expires in 1 day)`, `Note: 2 meal(s) proposed for 2 expiring item(s).`, `Proposal (nothing applied yet):` with `  Day 1 BREAKFAST: Spinach Mushroom Omelette - Uses Spinach (expires in 1 day)` and `  Day 1 SNACK: Red Lentil Soup - Uses Red lentils (expires in 1 day)`; the first `plan show` is unchanged; `Applied: Add 2 leftover meal(s) to the plan.`; one `undo` prints `Undone: Add 2 leftover meal(s) to the plan` and both meals disappear.
- **AI:** none by default. Optional: `plan leftovers --ai` lets the model only reorder the same safe recipes (`Note: Ranked with AI assistance ...`); without a model it prints the deterministic proposal and then one `AI explanation unavailable: ...` line.
- **Error cases:** `plan leftovers --days 0` prints the usage line; with no expiring items `No pantry items expire within 3 days; nothing to do.`; with a full plan `The plan has no empty slots. Use --overwrite ...`, and `plan leftovers --overwrite` marks replacements with `[replaces ...]`; with a vegetarian profile and only chicken expiring: `No safe recipe in your recipe book uses: Chicken breast. Try: recipe generate "a dish using Chicken breast"`.
- **GUI:** on the Pantry tab add "Red lentils" with tomorrow's expiry date (the hint shows "1 item expires within 3 days"); on the Plan tab generate a 3-day plan; back on the Pantry tab press **Find meals**: the rows show `Day 1 (Monday) BREAKFAST | Red Lentil Soup | Uses Red lentils (expires in 1 day)` under "Proposal (nothing applied yet):" and the Plan grid is unchanged; press **Apply**: the status bar says `Applied: Add 1 leftover meal(s) to the plan. ...`, the Plan grid and the Shopping List update; press **Undo** once and everything reverts; Find meals again and **Discard**. Error cases: Days `0` shows a red field error; after **New plan** on the Plan tab, Apply on an old proposal shows `The plan changed since this proposal was made. Run 'plan leftovers' again.` With AI configured, tick **Re-rank with AI**: the rows are reordered in the background and the Agent activity log fills; without AI the box is disabled with a visible "AI unavailable" label.
- **Say:** "Matching is by ingredient name only; apply is one undoable step."
- **Timestamp:** V7 @ 0:09 (CLI); V8 @ 0:10 (GUI setup), V8 @ 1:13 (GUI demonstration)

## F07 AI ingredient substitution (V7 CLI, V8 GUI; needs a real model)

- **Interface:** CLI, then the GUI Recipes tab's "Substitute an ingredient" section (an ingredient choice and a button instead of Stage 1's right-click "Suggest Substitute..." dialog; say so).
- **CLI input:** `profile allergy add peanut` (optional), `pantry add "Sunflower seed butter" 200 g`, `recipe show R109`, `recipe substitute --id R109 --ingredient "peanut butter" --reason allergy`, `recipe show R109` (unchanged), `recipe substitute apply 1`, `recipe show R109`, `undo`.
- **Expected output:** `[agent] ...` activity, then `Substitutes for Peanut butter in R109 Peanut Butter Banana Toast (reason: allergy):` with up to three numbered lines such as `  1. 2 tbsp Sunflower seed butter (x1) - ... [in pantry]` and `     about ... kcal, P ... g, C ... g, F ... g (AI estimates)`, the apply hint and the `Agent: ...` stats line; after apply: `Substituted: Substituted Peanut butter with ... in Peanut Butter Banana Toast`, `Per serving: 457 -> ... kcal`, `Warning: Step 2 may still mention Peanut butter; steps are not rewritten`, `Note: R109 is a bundled sample recipe; this change lasts for this session only.`; `undo` prints `Undone: Substitute Peanut butter with ...`.
- **GUI:** open the **Recipes** tab, click **R109 Peanut Butter Banana Toast** in the table, scroll the Details section down to **Substitute an ingredient**; choose **Peanut butter** in **Ingredient** and **Allergy** in **Reason**; press **Suggest substitutes**: the Agent activity log fills, then the table shows up to three rows (#, Replacement such as `2 tbsp Sunflower seed butter (x1)`, Impact, Pantry `in pantry`, Nutrition `about ... kcal, ... (AI estimates)`) under "Suggestion (nothing applied yet)" and the status bar ends with `Agent: ... model calls ...`; the recipe text above is unchanged. Click the first row, press **Apply selected**: the status bar shows `Substituted: Substituted Peanut butter with ... Per serving: 457 -> ... kcal Warning: Step 2 may still mention Peanut butter; steps are not rewritten Note: R109 is a bundled sample recipe; this change lasts for this session only. Use 'undo' to revert.` and the ingredient list shows the replacement; press **Undo** once and the original returns. Suggest again and press **Discard** to drop it. Error case: add the matching allergen on the Profile tab after suggesting, then Apply selected shows `Refused: ...` and nothing changes; without AI the Suggest button is disabled with a visible "AI unavailable" label.
- **AI:** the model only suggests names, ratios and nutrition estimates; code validates them, drops unsafe options before they are shown and checks the chosen one again before applying.
- **Error cases:** `recipe substitute --id R109 --ingredient nothing` lists the recipe's ingredients; `--reason banana` prints `Error: Unknown reason 'banana'. Use: allergy, missing, preference`; changing the profile so the chosen option becomes unsafe makes `apply` print `Error: Refused: ...` and nothing changes; with AI disabled one `Error: AI features are disabled: ...` line.
- **Timestamp:** V7 @ 3:25 (CLI); V8 @ 4:20 (GUI)
