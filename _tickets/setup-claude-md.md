---
id: nid_xbgo7223zqolvxp9lgql6wdsc_E
title: "setup claude md"
status: in_progress
deps: []
links: []
created_iso: 2026-03-07T14:15:05Z
status_updated_iso: 2026-03-07T16:17:12Z
type: task
priority: 1 
assignee: nickolaykondratyev
---

Look through the the following and setup the /home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness/ai_input folder to take the best practices. NOTE this project is NOT THORG specific, but it does take dependency on asgardCore and will FOLLOW the simular practices of things like logging (using Out, OutFactory). Will follow similar practices of direct construcdtor injection. So we should take the Kotlin practices from thorg. While remembering that its not THORG specific. Read the deep memories as needed and bring the deep memories that makes sense.

The files that we are going to be reading (and their content is included below):
- ${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/submodules/thorg-root/CLAUDE.md
- ${THORG_KOTLIN_MP_ROOT}/CLAUDE.md

ALSO note let's KEEP CLAUDE to things that are not in code no need to put dev dependencies ETC. things that are easily go stale should not be in claude.md

--------------------------------------------------------------------------------

<file path="${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/submodules/thorg-root/CLAUDE.md">
<file name="1_high_level.md">
## Project Overview

Thorg is a Personal Knowledge Management (PKM) tool similar to Dendron & Obsidian. It's a local-first application that allows users to manage their personal knowledge.

## Repository Structure

The repository is organized as a monorepo with several key components:

- `/source/libraries/kotlin-mp/` - Kotlin MultiPlatform libraries (server backing the extension and core logic).
- `/source/app/thorg-vscode/` - VSCode extension in Typescript.
- `/dendron_notes/thorg-vault` - Project documentation as Dendron notes

## Operating Systems
All Desktop Operating Systems: Windows, MacOS, Linux.

## Notes
- <IMPORTANT>Anchor Points: @AnchorPoint("anchor_point.ID") - Keep exact same ID when moving/modifying code (only delete when code fully removed)</IMPORTANT>

## High level principles
### Light Client & Push logic down to the lowest allowed layer
VSCode TS → VSCode-agnostic → thorg-server → KMP libraries → asgard libraries → commonMain

### Library Dependency Constraints
**Allowed:**
- ✅ `thorg` → `asgard`
- ✅ `NodeJS` → `Shared`

**Not Allowed:**
- ❌ `asgard` → `thorg` (asgard must NOT depend on thorg)
- ❌ `Shared` → `NodeJS` (Shared modules must NOT depend on NodeJS)
- ❌ `React UI libs (thorg-wysiwyg-editor-ts, thorg-react-flow)` → `thorg-core-shared-ts` (use `thorg-core-data-only-ts` instead)

- `KotlinJS` is only consumed by `thorg-vscode`
    - Changes to KotlinJS package names/structure only need to coordinate with thorg-vscode
    - No external consumers or backward compatibility concerns for JS outpu

</file name="1_high_level.md">
<file name="2_principles.md">
## Common Principles
### DRY (Don't Repeat Yourself) - HIGH IMPORTANCE
- Avoid knowledge duplication by abstracting common logic into clearly named functions and classes
- Every piece of knowledge must have a **SINGLE** representation within a system
- Abstract common patterns rather than copying code
- This is **MOST** important for business rules. Less of importance in test code.

### Follow SOLID Principles
- Follow all SOLID principles in design decisions
- Emphasize composition and clear interfaces

#### Especially: Single Responsibility Principle (SRP) - HIGH IMPORTANCE
- **Classes**: Should have ONE reason to change (one responsibility)
- **Functions/Methods**: Should do ONE thing, and do it well
- Makes code easier to understand and maintain

### Be Explicit
- Obvious code is good code
- Don't use `Pair`, `Triple` - create descriptive `data class` instead
- Clear naming over clever code
- Explicit is better than implicit

### KISS (Keep It Simple)
- Simple is GOOD, complex is BAD
    - Simple means straightforward.
    - Does NOT mean HACKS, **NEVER HACK**.
- Do NOT over-engineer
- Unused code is NOT good
- Apply **Pareto's Principle** (80/20)
- Scoped down and focused is good

### NO-Unsupervised Hacks
- Hacks are BAD
- **IF** you ever find yourself doing something that feels hacky you are NOT allowed to do it without **Explicit Human Approval**

### Respect Ownership Boundaries
- **Don't act on behalf of another system's responsibilities**
- Don't touch artifacts owned by another system (files, processes, shared state).
- Heuristic: **If you don't own it, don't write/delete it — make your reads smarter.**

## Code Organization
### Keep Cognitive Load Down
- Critical priority: minimize cognitive load
- Extract/abstract OR inline/simplify as appropriate
- Decide case-by-case for maximum clarity
- Balance abstraction vs simplicity

### Composition Over Inheritance
- USE **COMPOSITION** over **INHERITANCE** everywhere (including test code)
- Avoid inheritance hierarchies

### Anchor Points
- Use `@AnchorPoint("anchor_point.ID")` to mark important code locations
- **Keep exact same ID** when moving/modifying code
- Only delete when code is fully removed

### Manual Dependency Injection
- Constructor injection only.
- No singleton pattern.
- Instead of Singletons: Single instances via top-level wire-up.

### Favor Immutability
- Use immutable data structures by default
- Pass values as parameters, return new values
- Prevents race conditions, ensures pure functions

### Resource Management
- Proper resource disposal (files, sockets, connections, etc..).
- No resource leaks.
- No memory leaks.

## Tests and User Behavior are King
- As part of testing write tests to capture User Behavior (not just mock unit tests)
- Do not remove pre-existing or adjust user behavior tests WITHOUT explicit approval from a human.

## Documentation and Comments
- Code explains **WHAT** & **HOW**; comments explain **WHY**.
- **Documentation**:
    - **Exception**: WHAT comments are good and **expected** when WHAT is NON-OBVIOUS, such as:
        - Dealing with cryptic or non-intuitive APIs
        - TERSE expressions (eg. regexes, jq mappings etc..)
- **KDoc/TSDoc**: succinct and clear, not verbose.
- Keep related docs up-to-date: `CLAUDE.md`, deep memories, `thorg://notes/XXX`.
    - Make sure you follow the pattern of proper CLAUDE.md (generated from auto_load, or written directly).
- Preserve `anchor_points.XXX` identifiers as you modify code.

### Example of GOOD WHAT comment.
```bash
    # [git add --intent-to-add]: records untracked files in the index with empty content,
    # making them visible to `git diff` without staging their contents.
    git add --intent-to-add .
```

## Thin client
</file name="2_principles.md">
<file name="3_security_guidelines.md">
### Security Guidelines
- **Never roll your own security implementations**: Use battle-tested cryptographic libraries
- **Sanitize inputs**: Always validate and sanitize user inputs
- **No sensitive data in logs**: Use structured logging to redact sensitive values
  </file name="3_security_guidelines.md">
  <file name="3_tenets_thin_client_logic_in_model_layer.md">
### Thin Client
---
title: Thin Client (Tenet)
id: 7noxlb7qcaxq22c36ae38b374
---

Tenet: We will favor thin client (less TS code), and move logic behind the server. To accomplish this we will take the tiny performance hit that is often imperceptible to the user to go to the server.

With that said for the performance hit to be imperceptible we will need to create specific APIs for the views so that

1. We can actually follow the tenet of thin client and have the core logic live in the server (to avoid ending up with client having to piece many APIs ending up with more logic than we wanted on the client).
2. We can avoid many round trips that could sum up to be perceptible to the user.


### Push logic down
Thin client is a part of broader principle of **push logic down**.

Push logic down from UI libraries into libraries that do not have UI dependencies. Push logic down from `thorg` specific to `asgard` non-thorg specific layers.

Keep pushing it down out of the Typescript Client and into the Kotlin side.

Keep pushing it down in the kotlin libraries down to thorg-core and model layers.

And finally keep pushing it down if the logic is not Thorg specific into Asgard Layer.

### Related

* [round-trip-to-client](thorg://notes/3yzeq87pjow4f04dddqbsgr)

### S-VM-V: Service → ViewModel → View Architecture
**S-VM-V pattern**: Services hold shared business logic (the traditional "Model"), ViewModels hold per-view logic, and Views are the thin client UI. The data flow is **Service → ViewModel → View**.

Per-view API endpoints follow a consistent naming convention across all layers, making it easy to find the logical code backing the views.

| Layer | Naming Pattern | Location                                             |
|-------|---------------|------------------------------------------------------|
| **Controller** | `{ViewName}Controller` | `thorgServer/controller/vsc/`                        |
| **ViewModel** (interface + impl) | `{ViewName}ViewModel` / `{ViewName}ViewModelImpl` | `thorgCore/kernel/internalApi/viewModel` (same file) |
| **Service** | `{Name}Service` | `thorgCore/kernel/internalApi/service/`              |
| **TS Client** (interface) | `{ViewName}Client` | `thorg-core-shared-ts/thorgClient/`                  |
| **TS Client** (impl) | `{ViewName}ClientImpl` | `thorg-core-shared-ts/thorgClient/impl/`             |

**Key rules:**
- **Controllers are 1:1 with views. One controller per view, one viewModel per controller.**
- Interfaces and implementations live in the same file — interface at the top, impl at the bottom.
- Services contain shared business logic used by multiple viewModels (e.g., `MarkdownSyncService`).
- ViewModels compose services; controllers delegate to viewModels.
- `ThorgServerConstants.VSC.PerView` organizes URL paths by view.
- New per-view logic should live in the kotlin **viewModel** layer (`${THORG_KOTLIN_MP_ROOT:?}/thorgCore/src/jvmMain/kotlin/com/thorg/core/kernel/internalApi/viewModel`)
- No business logic in controllers.
- SSE events go through EventSystem so that we track events in one place.
- **CONSISTENCY_OVER_OPTIMIZATION**: make sure we follow the same S-VM-V pattern across the board for new views.

**Accepted tradeoff**: We accept boilerplate duplication across per-view endpoints and viewModels in favor of clarity. The clarity of understanding the logic of views without having to look at the views. This is not knowledge duplication — the heavy lifting lives in shared Services below the viewModel layer.

**VS-Webviews** are decoupled from VSCode by means of relying on the server.

**Consistency over Optimization — always route through ViewModel + EventSystem**: Even when an endpoint is a pure signal relay (zero domain logic, e.g., undo/redo), it MUST go through the ViewModel and emit an internal event through the EventSystem. Do NOT shortcut by broadcasting SSE directly from the controller. The cognitive cost of "sometimes events go through EventSystem, sometimes they don't" outweighs the small boilerplate of a relay ViewModel method + SseEventTranslator.

### Controller operation naming - RPC style
We use RPC-style endpoint naming rather than REST-style resource naming. This is because we often use `POST` for retrieve operations (to allow passing JSON payloads), so the HTTP method alone doesn't convey intent.

**Naming conventions:**
- Retrieve operations: `get-` prefix (e.g., `get-document-state`)
- Mutation operations: use a verb that describes the action (e.g., `update-content`)
  </file name="3_tenets_thin_client_logic_in_model_layer.md">
  <file name="4_1_thorg_notes.md">
## Thorg Notes for Documentation 💡

Code references notes via `[title](thorg://notes/<NOTE_ID>)` links. Notes are in `${THORG_ROOT}/dendron_notes/thorg-vault/`.

### Accessing Notes

**By ID:**
```bash
rg -l -g "*.md" "id:.*${NOTE_ID}" ${THORG_ROOT}/dendron_notes/thorg-vault | xargs cat
```

**By name:** `cat ${THORG_ROOT}/dendron_notes/thorg-vault/${note_exact_name}.md`

**Search:** Use ripgrep on the vault directory.

### Note Structure

- **Wiki links:** `[[note_name]]` or `![[transcluded_note]]`
- **Anchor points:** unique in the format of `anchor_point.UUID.E`/`ap.UUID.E` (older APs don't have .E).

### Maintenance

When updating code with note references, update related notes. Keep documentation SUCCINCT² — focus on relationships and high-level concepts; low-level details should be discoverable via AnchorPoints.
</file name="4_1_thorg_notes.md">
<file name="4_2_anchor_points.md">
### BASH Functions to help with anchor points
Finds all files containing a given anchor point or references to it.

## Usage
```bash
anchor_point.find_anchor_point_and_references <line_with_anchor_point> [dir]
```

## Arguments

| Argument                 | Required | Default         | Description                                                                        |
|-----|----------|-----------------|-----------|
| `line_with_anchor_point` | yes      | —               | A line containing an [ref.]`anchor_point.XXX.E`/`ap.XXX.E` definition or reference |
| `dir`                    | no       | `git.repo_root` | Directory to search in. Defaults to repo root.                                     |

## Examples
```bash
anchor_point.find_anchor_point_and_references '# @AnchorPoint("anchor_point.RnGWVoXwGi7H14iWzjrELW7c.E")'
anchor_point.find_anchor_point_and_references '# The dependencyX is at ref.anchor_point.RnGWVoXwGi7H14iWzjrELW7c.E' ~/projects/repo
```

## Notes

- The anchor point ID is extracted from the line via `parse_line_with_anchor_point_to_json`
- Search is performed using `rgfiles` (ripgrep-based), scanning for both definitions and references
  </file name="4_2_anchor_points.md">
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

```bash
# We overrode the change log dir:
export CHANGE_LOG_DIR="${THORG_ROOT:?}/_change_log"
```


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

### The tags are shared between change log and ticket
<tags to use for change_log and tk>
editor.native     # vscode native editor tag
editor.thorg      # what you see is what you get editor tag
thorgCore         # kotlin mp thorg core library
thorgCore.search  # kotlin-mp thorg search
thorgCore.kernel  # kotlin-mp thorg kernel (internal API & model layer)
thorgCore.graph   # kotlin-mp note graph
thorgCore.git     # kotlin-mp thorg git operations
thorgCore.markdown # kotlin-mp markdown processing
asgardCore        # kotlin mp asgard core foundation library
asgardGit         # kotlin mp asgard git library
tsLib.dataOnly    # thorg-core-data-only-ts (pure types/data, no runtime)
tsLib.coreShared  # thorg-core-shared-ts
vscode            # vscode extension (TS client)
vscode.webview    # vscode webview panel views
vscode.commands   # vscode extension commands
</tags to use for change_log and tk>

### WHEN Closing ticket THEN compress change log
When you close the ticket see which change_log files have been created on your branch and compress them into one coherant change log.



</file name="5_ticket_and_change_log_usage.md">
<file name="6_log_file_locations.md">
## Log File Locations

Both server and VSCode client logs use **JSON lines** format and **daily UTC rotation**.

### Primary Location (workspace)
`$WORKSPACE/.thorg/tmp/logs/`

| Component | File Pattern | Example |
|-----------|-------------|---------|
| Server (Kotlin) | `server_logs.YYYY_MM_DD.log[.N]` | `server_logs.2025_11_14.log` |
| VSCode Client | `vsc_client.YYYY_MM_DD.log[.N]` | `vsc_client.2025_11_14.log` |

### Backup / Pre-Init Location (home)
`$HOME/.thorg/not-under-scm/logs/`

| Component | File Pattern |
|-----------|-------------|
| Server (Kotlin) | `server_logs.YYYY_MM_DD.log[.N]` |
| VSCode Client (pre-init) | `vsc_client_pre_init.YYYY_MM_DD.log[.N]` |

- `[.N]` = optional size-rotation suffix (`.1`, `.2`, etc.)
- Server: custom `RotatingLogFileJsonSink` in asgard-core
- Client: Winston with `winston-daily-rotate-file` (20MB per file, 30-day retention)
  </file name="6_log_file_locations.md">
  <file name="7_lsp_usage.md">
## LSP Tools: Prefer Over File Search When Available

When LSP tools are active, **prefer them over Grep/Glob** for code navigation — LSP results are semantic, not textual.

| Task | Prefer |
|------|--------|
| Find all usages | `findReferences` over Grep |
| Jump to definition | `goToDefinition` over Grep |
| Get type / signature info | `hover` over reading files |
| List symbols in a file | `documentSymbol` over parsing |
</file name="7_lsp_usage.md">
<file name="8_claude_md_and_memory_files.md">
## When to update
### CLAUDE.md - updates
- `CLAUDE.md` is generated. DO NOT edit `CLAUDE.md` directly.
- `claude_md.generate.all_thorg` uses `$PWD/ai_input/memory/auto_load` files to generate the `CLAUDE.md`.
- To update CLAUDE.md (do when auto_load files need updates)
    - EDIT: `$PWD/ai_input/memory/auto_load` files
    - RE-RUN: `claude_md.generate.all_thorg`.

### WHEN to update what
- `ai_input/memory/auto_load`: for long term patterns and learnings that should be loaded every time.
- DEEP_MEMORY: for long term learning that should be loaded conditionally.
- AUTO memory (Claude code MEMORY.md and company): for shorter term learnings.
- change_log: for changes that were recently made.
- tk (ticket): for resolution of current work, and **follow-up**/todo tasks.

## When to load
### DEEP_MEMORY
DEEP_MEMORY: Separate files with deeper context. We start with just the pointer to these files.

1) DO LOAD the full content of these files when you need to work on the related topic.
2) DO Keep the content of files updated when you are working on the related topic.
3) DO create new deep memory files when you need to capture new long-term learnings that are cross-cutting (do not fit into sub-sub folder CLAUDE.md) and should not be autoloaded. See `${THORG_DEEP_MEMORY}/doc/how_to_create_deep_memory.md`

### Sub-project CLAUDE.md files
When working on a subproject, proactively read its CLAUDE.md before starting work.
These are NOT autoloaded when the conversation starts from the repo root.

Known CLAUDE.md locations:
- `${THORG_ROOT}/source/app/thorg-vscode/CLAUDE.md` — VSCode extension context.
- `${THORG_ROOT}/source/libraries/kotlin-mp/CLAUDE.md` - Kotlin Core logic context (server and core libraries).
  </file name="8_claude_md_and_memory_files.md">
  <file name="9_generated_pointers_to_deep_memories.md">
  <deep_memory_pointers>
## DEEP_MEMORY- READ AS NEEDED (keep contents updated)

| File | Description |
|------|-------------|
| ${THORG_DEEP_MEMORY}/1_general/dont_log_and_throw.md | Do NOT log and throw. The only exception is top most layer. |
| ${THORG_DEEP_MEMORY}/1_general/dont_use_blind_delays.md | Never use blind delays, even in tests. Use await mechanisms or polling instead. Only delay to prevent tight loops in continuous operations. |
| ${THORG_DEEP_MEMORY}/1_general/favor_semi_functional_programming_avoid_manual_loops.md | Prefer functional operations over manual loops with index tracking |
| ${THORG_DEEP_MEMORY}/1_general/operational_messages_use_brackets_for_values.md | Delineate values with brackets ([]) in operational messages. In production, use strongly typed [Val] classes—don't embed values in strings. |
| ${THORG_DEEP_MEMORY}/1_general/prefer_robustness_over_micro_optimization.md | Same Big-O, simpler wins—don't trade robustness for constant factors. |
| ${THORG_DEEP_MEMORY}/1_general/principles__prioritize_DRY_over_KISS.md | If there is contention between DRY (Don't Repeat Yourself) and KISS (Keep It Simple, Stupid), prioritize: DRY. |
| ${THORG_DEEP_MEMORY}/1_general/val_type_in_out_logging_are_value_specific.md | 'ValType' in Out logging is VERY specific, and must logically fit. |
| ${THORG_DEEP_MEMORY}/doc/how_to_create_deep_memory.md | How to create, update, and regenerate deep memories for Claude context management. |
| ${THORG_DEEP_MEMORY}/doc/how_to_update_claude_md.md | How to update CLAUDE.md files: generated (via auto_load) vs ad-hoc (direct edit). |
| ${THORG_DEEP_MEMORY}/for_tests/in_tests__aim-for-one-assert-per-test-block.md | Structure tests with separate `it` blocks for each assertion. Use describe to group. Self-documenting. |
| ${THORG_DEEP_MEMORY}/for_tests/in_tests__avoid_duplicate_given_when.md.md | In tests: Use GIVEN/WHEN/THEN (with AND for branching) when writing tests. Avoid duplicating GIVEN or WHEN statements. |
| ${THORG_DEEP_MEMORY}/for_tests/in_tests__be_DRY_by_using_data_driven_tests.md | In tests DRY by using data-driven tests to eliminate code duplication. |
| ${THORG_DEEP_MEMORY}/for_tests/in_tests__fail_hard_never_mask.md | DO not skip individual tests. Tests must fail explicitly when dependency, setup or configuration is missing. NEVER write code that silently degrades or skips individual test functionality. Only entire test class is allowed to be enabled/disabled based on environment. NOT individual tests. Please fail explicitly instead. |
| ${THORG_DEEP_MEMORY}/kotlin/5_js_kotest_use_flat_structure.md | For Kotlin tests with **Javascript target** use StringSpec flat test structure. |
| ${THORG_DEEP_MEMORY}/kotlin/5_js_target_support.md | Guide on JavaScript target support in Kotlin Multiplatform libraries. |
| ${THORG_DEEP_MEMORY}/kotlin/detekt_static_analysis.md | How detekt static analysis is configured, how to run it, and how baselines work across KMP and JVM modules. Aim: to reduce detekt exceptions. |
| ${THORG_DEEP_MEMORY}/kotlin/out_logging_in_kotlin_mp.md | How to use Out structured logging in Kotlin MP. Covers Out interface, Val, ValType, and lazy debug patterns. |
| ${THORG_DEEP_MEMORY}/kotlin/testing_with_race_conditions_with_chaos_snail.md | When there is a need to test race conditions use ChaosSnail to control order |
| ${THORG_DEEP_MEMORY}/server/how_to_call_thorg_server_from_cli.md | How to call thorg server from CLI. |
| ${THORG_DEEP_MEMORY}/server/how_to_run_thorg_server.md | How to run thorg-server (separately from visual studio code). |
| ${THORG_DEEP_MEMORY}/vscode/how_to_create_new_ts_ui_library.md | Steps to create a new TypeScript UI library in the Thorg VSCode extension. Also contains universal TS library rules (e.g., test file exclusion from build tsconfigs) that apply to ALL TS libraries including core-shared. |
| ${THORG_DEEP_MEMORY}/vscode/in-test-code-be-liberal-with-library-usage.md | In test code, be liberal with library usage, especially for OS-specific functionality |
| ${THORG_DEEP_MEMORY}/vscode/markdown-auto-save-rationale.md | Why auto-save for markdown files: Kotlin server modifies files during refactoring |
| ${THORG_DEEP_MEMORY}/vscode/playwright-e2e-with-vscode.md | Playwright E2E Testing for VSCode Extension |
| ${THORG_DEEP_MEMORY}/vscode/troubleshoot-vscode-with-playwright.md | Troubleshoot VSCode extension issues using Playwright and diagnostic logging. Use this for quick validation within VScode as well. |
| ${THORG_DEEP_MEMORY}/vscode/type-module-cjs-dist-mismatch-causes-blank-webview.md | Never add type:module to package.json when dist/ output is CJS — causes blank webview (exports is not defined) |
| ${THORG_DEEP_MEMORY}/vscode/vscode-configuration-defaults-vs-configuration.md | Distinction between contributes.configuration and contributes.configurationDefaults in VSCode extensions |

</deep_memory_pointers>
</file name="9_generated_pointers_to_deep_memories.md">

</file path="${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/submodules/thorg-root/CLAUDE.md">


<file path="${THORG_KOTLIN_MP_ROOT}/CLAUDE.md">
<file name="0_project_overview.md">
## Project Overview

This Kotlin MultiPlatform project is part of Thorg, a Personal Knowledge Management (PKM) tool similar to Dendron & Obsidian.

### Purpose
- Local-first PKM with Markdown notes
- Cross-platform: JVM, JavaScript (Browser + Node.js)
- Lucene-based full-text search
  </file name="0_project_overview.md">
  <file name="1_structure.md">
### Library Architecture

- **`asgard*` modules**: General-purpose, lower-level libraries (asgardCore, asgardGit, asgardTestTools, etc.)
- **`thorg*` modules**: Application-specific code (thorgCore, thorgSandbox, etc.)
- Modules with JS target (in addition to JVM target) are split into two layers:
    - **`xxxShared` modules**: Libraries with additional Browser & NodeJS target support.
    - **`xxxNodeJS` modules**: Libraries with additional NodeJS support.
    - More info on JS see ${THORG_KOTLIN_MP_ROOT:?}/ai_input/memory/deep/5_js_target_support.md
- **Core constraint**: thorgCore must not include UI or server dependencies

### Module Structure

Each module follows this pattern:

```
moduleName/
├── moduleName.build.gradle.kts  # Named after module
├── src/
│   ├── commonMain/kotlin/       # Platform-independent code (best place for logic code)
│   ├── commonTest/kotlin/       # Platform-independent tests (primarily used in [xxxShared, xxxNodeJS] packages)
│   ├── jvmMain/kotlin/          # JVM-specific code
│   ├── jvmTest/kotlin/          # JVM-specific tests (for non [xxxShared, xxxNodeJS] put tests here).
│   ├── jsMain/kotlin/           # JavaScript-specific code (for JS-enabled modules)
│   └── jsTest/kotlin/           # JavaScript tests (avoid this use [commonTest] instead)
└── gradle.properties           # Module-specific properties
```
</file name="1_structure.md">
<file name="2_build.md">
### Build Commands

Build the entire project:

```bash
./gradlew buildLibs
```

Build specific module (for JVM only preferred for faster iteration)

```bash
./gradlew "thorgCore:compileKotlinJvm" "thorgCore:compileTestKotlinJvm"
```

Clean and compile:

```bash
./gradlew clean
./gradlew compile
```
</file name="2_build.md">
<file name="2_test.md">

### Testing

#### Turning on console out from tests (println to work)

```bash
export ASGARD_TEST_CONSOLE_OUTPUT_ENABLED=true
```

#### Output to tmp file from test (to keep debug output more focused than println)

```kt
val file = com.asgard.core.file.file("./.tmp/<name-tmp-file>")

file.appendText("text to append")
```

#### Finding log output of last failed test

When test fails all the output from `Out` that was accumulated will be dumped:

1) To unique file (see failed test output) AND 2) to cached location at
   `$PWD/.out/tmp/last_dumped_out_lines_from_test.jsonl`.

#### Running Tests

Run all unit tests:

```bash
# Output of tests is quite verbose so pipe it to a file instead of console
# 
# [eai2_capture_out] will redirect STDOUT/STDERR to files
eai2_capture_out ./gradlew jvmTest
```

Run a single test class:

```bash
export ASGARD_RUN_INTEG_TESTS=true && ./gradlew singleJvmTestAuto -PtestClass="<fullClassName>"

# Example:
export ASGARD_RUN_INTEG_TESTS=true && ./gradlew singleJvmTestAuto -PtestClass="com.thorg.core.shallowParse.impl.NoteNameUtilTest"
```

Note: `singleJvmTestAuto` automatically finds which module contains the test class, so you don't need to specify
`<libName>:`.

### Integration Tests

Enable integration tests by setting environment variables:

```bash
export ASGARD_RUN_INTEG_TESTS=true
export ASGARD_RUN_LOAD_INTEG_TESTS=true
export ASGARD_RUN_LOCAL_GIT_SERVER_INTEG_TESTS=true

./gradlew jvmTest
```

</file name="2_test.md">
<file name="3_kotlin_development_standards.md">
# Kotlin Development Standards

## Core Principles
You should already have the following in your context `${THORG_ROOT:?}/ai_input/memory/auto_load/2_principles.md` make sure to follow them.

## Kotlin specific
### 5. Always Favor Compile-Time Checking Over Runtime
- Push checks to compilation time instead of runtime
- Let the compiler catch errors early

**Avoid `else` branches in `when` expressions for sealed classes/enums:**

```kotlin
fun SomeElement.convert(): ThorgElement = when (this) {
    // ✅ DO: Handle all cases explicitly
    is X -> ThorgX
    is Y -> ThorgY
    // ❌ DON'T: Hide missing cases with else - prevents compile-time checking
    // else -> throw IllegalArgumentException("Unknown: $this")
}
```

## Coroutines: Avoid `runBlocking`

`runBlocking` **loses coroutine context** (including request IDs for logging) and blocks the calling thread. It should be a **last resort**.

### Where `runBlocking` is acceptable
- **Tests** — bridging test code to suspend functions is fine
- **`main()` entry points** — CLI/server entry points must be synchronous
- **Framework-mandated sync callbacks** — Micronaut `@PostConstruct`/`@PreDestroy`, `ApplicationEventListener`, Reactor callbacks, DI `@Factory` methods
- **Java API contract overrides** — e.g., `toString()` override that needs mutex

### Where `runBlocking` is NOT acceptable
- **Library/core code** — if the caller chain is already suspend, keep it suspend all the way down
- **Inside suspend functions** — never nest `runBlocking` within a coroutine
- **Performance-critical paths** — blocking a thread defeats the purpose of coroutines

### What to do instead
```kotlin
// ❌ DON'T: Non-suspend function wrapping suspend with runBlocking
fun getData(): Data {
    return runBlocking { repository.fetchData() }
}

// ✅ DO: Make the function suspend
suspend fun getData(): Data {
    return repository.fetchData()
}
```

When you encounter a non-suspend function that uses `runBlocking`, push `suspend` **upward** through the call chain until you reach a framework boundary (controller, listener, entry point).

## Thread-Safety is a MUST
- Use mutexes where necessary.
- Double check libraries to adhere with their thread safety.
- Use synchronized wrappers like MutableSynchronizedList

## Exception Handling

### Structured Exceptions
- All exceptions MUST be descendants of `AsgardBaseException`
- User-caused exceptions should be descendants of `AsgardUserCausedException`

## Logging

### Use Structured Logging with Specific ValType

```kotlin
private val out = outFactory.getOutForClass(OrderProcessor::class)

suspend fun processOrder(orderId: String) {
    // ✅ DO: Use specific ValType
    out.info("processing_order", Val(orderId, ValType.ORDER_ID))
    
    // ❌ DON'T: Use println
    // println("Processing order: $orderId")
    
    // ❌ DON'T: Use incorrect ValType
    // out.info("processing_order", Val(orderId, ValType.STRING))
}
```

**Important:**
- ValType MUST be **specific** to the type of value being logged
- ✅ DO CREATE new ValType enumerations when there isn't a good fit already
- ❌ DON'T use incorrect ValType when using Val
- ❌ DON'T use println



## Kotlin Multiplatform Best Practices

### 1. Maximize Common Code
- Put as much code as possible in `commonMain`
- Minimize platform-specific implementations

### 2. Favor Kotlin MP Constructs Over Java
- Use Kotlin Multiplatform constructs instead of Java equivalents
- Example: `kotlinx.coroutines.sync.Mutex` instead of `synchronized`

### 3. Prefer Interfaces Over expect/actual Classes
- Use dependency injection with interfaces instead of expect/actual classes
- More flexible and testable approach

```kotlin
// ✅ DO: Interface-based approach
interface DateFormatter {
    fun format(timestamp: Long): String
}
expect fun createDateFormatter(): DateFormatter

// ❌ AVOID: expect/actual classes
expect class DateFormatter {
    fun format(timestamp: Long): String
}
```

### 4. Minimal Expectations
- Define only essential platform differences in `expect` declarations
- Keep platform-specific code to a minimum

## Refactoring Policy

### Clean Changes: Avoid `@Deprecated`
- Refactor directly rather than marking code as `@Deprecated`
- When deprecation is unavoidable:
    1. Execute as a multi-step process for safety
    2. Commit as you go

## Naming Conventions

### Gradle Build Files
Follow this pattern for easier searching in IntelliJ:
```
- rootDir/
  - rootDir.build.gradle.kts
  - settings.gradle.kts
  - subA/
    - subA.build.gradle.kts
  - subB/
    - subB.build.gradle.kts
```

**Reason:** Easier to search for build scripts in IntelliJ by package name

### JavaScript Target Naming
- `*Shared` modules: Browser + Node.js support
- `*NodeJs` modules: Node.js only support

## Note Mutation Policy
All note file modifications (create, update, delete, write) MUST go through `NoteMutator`.
Direct `file.writeText()` or equivalent for note files is NOT allowed.
`NoteMutator` ensures consistent event emission for index updates.

</file name="3_kotlin_development_standards.md">
<file name="4_testingStandards.md">
## Testing Standards
### Common Testing Rules
- **Prefer focused tests with descriptive names** over broad tests with comments.
- OK to use unorthodox test class&function names like SomeClass__XXX__YYY when it aids clarity.


</file name="4_testingStandards.md">
<file name="4_testingStandards.noJsTarget.md">
Testing standards for packages targeting JVM only. No JS target.

**NOT for "...Shared", NOT for **...NodeJS** packages.**


### Test Structure

**Use Kotest DescribeSpec** with GIVEN/WHEN/THEN pattern.

- **Unit tests**: Extend `AsgardDescribeSpec`
- New tests should be placed within `jvmTest`.
- **Integration tests**: IF its ThorgKernel related integ test THEN Extend `ThorgKernelDescribeSpec` (provides `kernel`
  property of type `ThorgKernelForTests`, and does kernel initialization & cleanup)
    - ThorgKernelForTests has `TestNoteFactory`
        - `TestNoteFactory` is useful for test note creation.
    - Note if you modify/create notes then call `kernel.awaitZeroOutstandingEventsWithoutDelay()` to ensure all events are
      processed prior to assertions.
- `delay` MUST not be used for synchronization. Instead, use sync primitives like `CountDownLatch`, `CountingLatch`,
  `CompletableDeferred`.

### Integration Test Configuration

Always guard integration tests:

```kotlin
@OptIn(ExperimentalKotest::class)
class MyIntegrationTest : ThorgKernelDescribeSpec({
  describe("integration test").config(enabledIf = { isIntegTestEnabled() }) {
    // test logic
  }
})
```
</file name="4_testingStandards.noJsTarget.md">
<file name="6_version_management.md">
## Version Management

All library versions are managed through **Gradle Version Catalog** in `gradle/libs.versions.toml`.

More details on management in **[version_management.md](./doc/claude_md/version_management.md)**

### Highlighted: Critical Version Constraints

⚠️ **Do not upgrade these without review**:

- **KSP**: Must use 1.x (currently 2.1.20-1.0.32), NOT 2.x (incompatible with Micronaut allopen plugin)
- **Micronaut**: Plugin version (4.5.5) differs from runtime version (4.9.3) intentionally

See comments in `gradle/libs.versions.toml` for details.
  </file name="6_version_management.md">

</file path="${THORG_KOTLIN_MP_ROOT}/CLAUDE.md">