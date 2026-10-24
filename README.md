# MealMind

MealMind Intelligent Meal Planning and Kitchen Management System for EECS 3311 (Software Design), Fall 2026.

**Student**: Abhishek Ramessur (219433564)

## Stages
- **Stage 1 (Design)** - [`stage1/Stage1_Design_Report.md`](Stage1/Stage1_Design_Report.md)
- **Stage 2 (Implementation)** - see [Stage 2: Setup and Configuration](#stage-2-setup-and-configuration)
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
| `LLM_MODEL` | `gpt-4o` | Model name sent to the provider |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama server |
| `LLM_TIMEOUT_SECONDS` | `30` | LLM request timeout (positive integer) |

### Using Ollama instead (no key)
Install Ollama, pull a model (for example `ollama pull llama3`), then in `.env` set:
```
LLM_PROVIDER=ollama
LLM_MODEL=llama3
```
`LLM_API_KEY` can be left as the placeholder or removed.

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
* CLI: _added in a later commit_
* GUI: _added in a later commit_

### Security
* Never commit `.env`. Run `git status` before committing.
* Never paste API keys into reports, screenshots, or demo videos.
* If a key is ever exposed, rotate (revoke and regenerate) it in the provider's dashboard right away. Deleting it from Git is not enough.
