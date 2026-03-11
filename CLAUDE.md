#generated_do_not_edit_directly
<file name="0_env-requirement.md">

## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)

### Asgard Libraries in Maven Local (required for build)

`./gradlew :app:build` requires `com.asgard:asgardCore:1.0.0` and `com.asgard:asgardTestTools:1.0.0`
to be present in `~/.m2`.

**Self-healing scripts** (`test.sh`, `test_with_integ.sh`) auto-publish missing libs before invoking
Gradle. No manual setup needed when using these scripts (ref.ap.gtpABfFlF4RE1SITt7k1P.E).

Check status manually:

```bash
./gradlew checkAsgardInMavenLocal
```

Publish manually if needed (e.g., when calling `./gradlew :app:build` directly):

```bash
export THORG_ROOT=$HOME/thorg-root
./gradlew publishAsgardToMavenLocal
```

Or use the pre-build script directly:

```bash
bash _prepare_pre_build.sh
./gradlew :app:build
```

### Why Gradle `dependsOn` Cannot Self-Heal

Gradle resolves maven coordinates (e.g. `com.asgard:asgardCore:1.0.0`) at **configuration time**,
before any task executes. A `dependsOn ensureAsgardInMavenLocal` wiring in `compileKotlin` cannot
heal a missing dependency — the build fails at configuration before the healing task can run.
`_prepare_pre_build.sh` solves this by running before Gradle starts.
</file name="0_env-requirement.md">
<file name="1_core_description.md">
## Project Overview

Codename: **CHAINSAW**. Package: `com.glassthought.shepherd`.

CLI Kotlin Agent Harness — replaces a top-level orchestrator agent with a Kotlin CLI process.
Sub-agents are spawned as independent processes with fully isolated context windows.

### Key Characteristics
- **CLI application** built in Kotlin (JVM)
- **Agent coordination**: manages workflow phases, file-based communication between agents
- **NOT thorg-specific** — general-purpose agent harness

### High-Level Architecture Decisions

**Ticket-driven**: Chainsaw always operates on a ticket (markdown file with YAML frontmatter containing `id` and `title`). The ticket is required input.

**CLI**: `shepherd run --workflow <name> --ticket <path>` via **picocli**.

**Agent invocation — TMUX only**: All agents spawned as interactive TMUX sessions. Strictly serial (one agent at a time) in V1. Separate session per phase — context carries via files.

**Agent↔Harness communication — bidirectional**:
- Agent → Harness: HTTP POST via `harness-cli-for-agent.sh` (wraps curl)
- Harness → Agent: TMUX `send-keys` / ref.ap.7sZveqPcid5z1ntmLs27UqN6.E
- Structured content delivered via temp files (write file, send path)

**HTTP server**: Ktor CIO, binds port 0 (OS-assigned), writes port to `$HOME/.shepherd_agent_harness/server/port.txt`. Starts once, stays alive across all phases.

**Workflow definitions**: JSON under `./config/workflows/`. Shared "parts" schema for both static and planner-generated workflows. **Jackson + Kotlin module** for all serialization.

**Two workflow types**:
- `straightforward` — static parts, no planning
- `with-planning` — PLANNER → PLAN_REVIEWER iteration loop, then dynamic execution from `plan.json`

**Role catalog**: Auto-discovered from `$CHAINSAW_AGENTS_DIR`. Every `.md` file is an eligible role; `description` extracted from YAML frontmatter.

**Session tracking**: `AgentSessionIdResolver` interface (`ClaudeCodeAgentSessionIdResolver` impl) — GUID handshake to discover Claude Code session IDs for resume.

**Harness decisions**: `DirectLLMApi` for iteration evaluation, title compression, etc. Structured JSON responses. Tiers: `QuickCheap`, `Medium`.

**Server endpoints (V1)**: `POST /agent/done` (task complete), `/agent/question` (Q&A, curl blocks until human answers), `/agent/failed` (unrecoverable error → `FailedToExecutePlanUseCase`), `/agent/status` (health ping reply). All requests include git branch as identifier.

**CodeAgent abstraction**: `CodeAgent.run(instructionFile, workingDir, publicOutputFile, privateOutputFile) -> AgentResult`. Instructions are Markdown files. `ClaudeCodeAgent` is the V1 implementation.

**Context assembly**: `ContextProvider` interface assembles context packages — agent instruction files (role definition + ticket + SHARED_CONTEXT.md + prior PUBLIC.md files + harness CLI help), iteration decision prompts, and planner instructions (ticket + role catalog).

**Phase transitions — hybrid**: Automatic for straightforward transitions (implementor → reviewer). LLM-evaluated for iteration decisions: `DirectLLMApi` receives reviewer's PUBLIC.md + reviewed role's PUBLIC.md + SHARED_CONTEXT.md, returns structured JSON (pass/fail + reason).

**Agent lifecycle**: TMUX session created → agent started → AgentSessionIdResolver GUID handshake → instruction file sent via `send-keys` → agent works (may call Q&A) → agent calls `/agent/done` → harness kills session → next phase.

**Health monitoring**: Timeout → ping via TMUX → crash detection. UseCase pattern (`NoStatusCallbackTimeOutUseCase`, `NoReplyToPingUseCase`, `FailedToExecutePlanUseCase`).

**Plan mutability**: Frozen during execution. Minor adjustments within a part OK. Major deviations → agent calls `/agent/failed` → cleanup agent enriches ticket → codebase reset → ticket re-opened.

**Resume**: `current_state.json` tracks workflow progress. On restart, offers to resume from last checkpoint.

**File structure**: `.ai_out/${git_branch}/` with `harness_private/`, `shared/`, `planning/`, `phases/` subdirectories.

**Git branching**: Derived from ticket. `{TICKET_ID}__{slugified_title}__try-{N}` (`__` delimiter). try-N starts at 1, increments on retry after failure.

### Dependencies
- Will take dependencies on well established third-party libraries.
- **Depends on asgardCore** (Out/OutFactory logging, ProcessRunner, AsgardCloseable, coroutines)
</file name="1_core_description.md">
<file name="2_claude_editing.md">
Keep claude.md and related documentation up to date.

Top level CLAUDE.md is generated by running CLAUDE.generate.sh and is fed by ai_input.
</file name="2_claude_editing.md">
<file name="3_kotlin_standards.md">
## Kotlin Development Standards

### Dependency Injection
- **Constructor injection only** — no DI framework, no singletons.
- Single instances wired at the top-level entry point.

### Logging
- Use `Out` / `OutFactory` for all logging (never `println`).
  - println is allowed to be used for user communication NOT logging.
  - Use `Val` with `ValTypeV2` for project specific types.
- Structured values via `Val(value, ValType.SPECIFIC_TYPE)` — never embed values in message strings.
- Use **lazy lambda** form for DEBUG/TRACE to avoid serialization overhead.
- Use **snake_case** for log message strings.
- `ValType` must be **semantically specific** to the value being logged.
- All `Out` methods are `suspend` functions.
- See deep memory: `out_logging_patterns.md` for full patterns.

### Exceptions
- Extend `AsgardBaseException` hierarchy for structured exceptions.
- **Do NOT log and throw** — let exceptions bubble up, log at the top-most layer only.
- See deep memory: `dont_log_and_throw.md`.

### Coroutines
- Avoid `runBlocking` — acceptable only at main entry points, tests, and framework callbacks.
- Use proper coroutine dispatchers via `DispatcherProvider`.
- Thread safety: use mutexes, not synchronized blocks.

### Code Style
- **Composition over inheritance** — always.
- Be classy and use interfaces.
  - Put interfaces as the same place as the default implementation.
  - Use naming that aligns with implementation or `Impl` as fallback naming (Doer/DoerImpl).
- **Favor immutability** — immutable data structures by default, pass values as parameters, return new values.
- **Be explicit** — no magic numbers, no `Pair`/`Triple`. Use descriptive `data class` instead.
- **No `@Deprecated`** — refactor directly, make clean breaks.
- **Favor functional style** — prefer `map`, `filter`, `zip`, `takeWhile` over manual loops with index tracking.
- **Resource management** — use `.use{}` pattern (AsgardCloseable). No resource leaks.
- **Sealed classes/enums in `when`** — no `else` branch; let compiler enforce exhaustiveness.
- **Favor compile-time checks** over runtime checks.
- **No free-floating functions** — favor cohesive classes; for stateless utilities, use a static class (companion object).
</file name="3_kotlin_standards.md">
<file name="4_testing_standards.md">
## Testing Standards

### Framework & Style
- **BDD with GIVEN/WHEN/THEN** using Kotest `DescribeSpec`.
- Unit tests extend `AsgardDescribeSpec`.
- Use `describe` blocks for GIVEN/AND/WHEN structure; `it` blocks for THEN assertions.

### Dependencies
- `AsgardDescribeSpec` comes from `testImplementation("com.asgard:asgardTestTools:1.0.0")`.
- Kotest deps must be declared explicitly (they are `implementation`, not `api`, in `asgardTestTools`):
  ```kotlin
  testImplementation(libs.kotest.assertions.core)  // io.kotest:kotest-assertions-core
  testImplementation(libs.kotest.runner.junit5)     // io.kotest:kotest-runner-junit5
  ```
- `outFactory` is **inherited** from `AsgardDescribeSpec` — do NOT construct `NoOpOutFactory` manually in tests.

### Integration Tests (environment-dependent)
- Gate entire `describe` blocks with `.config(isIntegTestEnabled())` for tests requiring external resources (e.g., tmux, network).
- Annotate the class with `@OptIn(ExperimentalKotest::class)`.
- Only entire test classes (or top-level describe blocks) may be enabled/disabled — NOT individual `it` blocks.
- `isIntegTestEnabled()` is defined in `app/src/test/kotlin/org/example/integTestSupport.kt` and reads the
  `runIntegTests` system property injected by Gradle. Enable via: `./gradlew :app:test -PrunIntegTests=true`.
  This is tracked as a Gradle task input so the cache invalidates automatically (unlike env vars).

### Integration Test Base Class (with ChainsawContext)
- For integration tests requiring `ChainsawContext` (tmux, LLM, etc.), extend `SharedContextDescribeSpec` instead of `AsgardDescribeSpec` directly.
- `SharedContextDescribeSpec` provides a shared singleton `ChainsawContext` and pre-configured `AsgardDescribeSpecConfig.FOR_INTEG_TEST` settings.
- No config required — defaults pull from `SharedContextIntegFactory`.
- Access shared deps via the `shepherdContext` property (e.g., `shepherdContext.infra.tmux.sessionManager`).
- See `SharedContextDescribeSpec` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) for full KDoc and examples.
- Location: `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt`

### Suspend Context
- `describe` block bodies are **NOT** suspend contexts.
- Suspend calls must go inside `it` or `afterEach` blocks.

### One Assert Per Test
- Each `it` block contains **one logical assertion**.
- The `it` description clearly states what is being verified.
- No inline WHAT comments needed — the nested describe/it structure IS the documentation.
- See deep memory: `in_tests__one_assert_per_test.md`.

### Fail Hard, Never Mask
- Tests must **fail explicitly** when dependencies, setup, or configuration are missing.
- **No silent fallbacks**, no conditional skipping of individual tests.
- Only entire test classes may be enabled/disabled based on environment — NOT individual tests.
- See deep memory: `in_tests__fail_hard_never_mask.md`.

### Synchronization
- **Do NOT use `delay`** for synchronization in tests. Use proper await mechanisms or polling.

### Data-Driven Tests
- Use data-driven tests to eliminate duplication when testing the same logic with multiple inputs.

### Naming
- Focused, descriptive test names that read naturally in the GIVEN/WHEN/THEN tree.
</file name="4_testing_standards.md">
<file name="5_ticket_and_change_log_usage.md">
### We use change log to track the changes.
<change_log_help>
change_log - git-backed changelog for AI agents

Usage: change_log <command> [args]

Commands:
  create [title] [options]  Create changelog entry (prints JSON)
    --impact N              Impact level 1-5 (required)
    -t, --type TYPE         Type (feature|bug_fix|refactor|chore|breaking_change|docs|default) [default: default]
    --desc TEXT             Short description (in query output)
    --details_in_md TEXT    Markdown body (visible in show, NOT in query output)
    -a, --author NAME       Author [default: git user.name]
    --tags TAG,TAG,...      Comma-separated tags
    --dirs DIR,DIR,...      Comma-separated affected directories
    --ap KEY=VALUE          Anchor point (repeatable)
    --note-id KEY=VALUE     Note ID reference (repeatable)
  ls|list [--limit=N]       List entries (most recent first)
  show <id>                 Display entry
  edit <id>                 Open entry in $EDITOR
  add-note <id> [text]      Append timestamped note (text or stdin)
  query [jq-filter]         Output entries as JSONL (requires jq for filter)
  help                      Show this help

Entries stored as markdown in ./_change_log/ (auto-created at nearest .git root)
Override directory with CHANGE_LOG_DIR env var

- BEFORE_STARTING: query for recent changes to gather the context of recent relevant changes that were made.
- AFTER_COMPLETION of TOP_LEVEL_AGENT work: Add change log entry with clear title, concise --desc, and use --details_in_md for longer explanations. Sub-agents should NOT write change log.
</change_log_help>

### We use ticket to track tasks
This project uses a CLI ticket system=[tk] for task management.

- USE `tk` to manage tasks outside of your current session.
- USE `tk` to create new ticket for follow-up items.
  - Including any found pre-existing test failures.
- USE `tk` to close tickets for completed tasks.

<tk help>
ticket - minimal ticket system with dependency tracking

Usage: ticket <command> [args]

Commands:
  create [title] [options] Create ticket, prints JSON with id and full_path
    -d, --description      Description text. Goes into markdown body of the ticket.
                           MUST be self contained, if referencing files make sure they are referenced
                           with full relative path from git repo. And NOT just the file names.
                           This should give GOOD context for new agent picking this up.
                           For newlines use bash $'...\n...' quoting, e.g.:
                             -d $'First line.\n\nSecond paragraph.\n- bullet'
    --design               Design notes
    --acceptance           Acceptance criteria
    -t, --type             Type (bug|feature|task|epic|chore) [default: task]
    -p, --priority         Priority 0-4, 0=highest [default: 2]
    -a, --assignee         Assignee
    --external-ref         External reference (e.g., gh-123, JIRA-456)
    --parent               Parent ticket ID
    --tags                 Comma-separated tags (e.g., --tags ui,backend,urgent)
  start <id>               Set status to in_progress
  close <id>               Set status to closed
  reopen <id>              Set status to open
  status <id> <status>     Update status (open|in_progress|closed)
  dep <id> <dep-id>        Add dependency (id depends on dep-id)
  dep tree [--full] <id>   Show dependency tree (--full disables dedup)
  dep cycle                Find dependency cycles in open tickets
  undep <id> <dep-id>      Remove dependency
  link <id> <id> [id...]   Link tickets together (symmetric)
  unlink <id> <target-id>  Remove link between tickets
  ls|list [--status=X] [-a X] [-T X]   List tickets
  ready [-a X] [-T X]      List open/in-progress tickets with deps resolved
  blocked [-a X] [-T X]    List open/in-progress tickets with unresolved deps
  closed [--limit=N] [-a X] [-T X] List recently closed tickets (default 20, by mtime)
  show <id>                Display ticket
  edit <id>                Open ticket in $EDITOR
  add-note <id> [text]     Append timestamped note (or pipe via stdin)
  query [jq-filter]        Output tickets as JSONL (includes full_path)

Searches parent directories for _tickets/, stopping at .git boundary (override with TICKETS_DIR env var)
Tickets stored as markdown files in _tickets/ (filenames derived from title)
IDs are stored in frontmatter at 'id' field;
</tk help>

### WHEN Closing ticket THEN compress change log
When you close the ticket see which change_log files have been created on your branch and compress them into one coherent change log.
</file name="5_ticket_and_change_log_usage.md">
<file name="z_deep_memory_pointers.md">
<deep_memory_pointers>
## DEEP_MEMORY- READ AS NEEDED (keep contents updated)

| File | Description |
|------|-------------|
| $(git.repo_root)/ai_input/memory/deep/dont_log_and_throw.md | Do NOT log and throw. Let exceptions bubble up, log at the top-most layer only. |
| $(git.repo_root)/ai_input/memory/deep/favor_functional_style.md | Prefer functional collection operations (map, filter, zip, takeWhile) over manual loops with index tracking. |
| $(git.repo_root)/ai_input/memory/deep/in_tests__fail_hard_never_mask.md | Tests must fail explicitly. No silent fallbacks, no conditional skipping of individual tests. Only entire test classes may be toggled. |
| $(git.repo_root)/ai_input/memory/deep/in_tests__one_assert_per_test.md | Structure tests with separate `it` blocks for each assertion. Use describe to group. Self-documenting. |
| $(git.repo_root)/ai_input/memory/deep/out_logging_patterns.md | How to use Out structured logging. Covers Out interface, Val, ValType, and lazy debug patterns. |

</deep_memory_pointers>
</file name="z_deep_memory_pointers.md">
