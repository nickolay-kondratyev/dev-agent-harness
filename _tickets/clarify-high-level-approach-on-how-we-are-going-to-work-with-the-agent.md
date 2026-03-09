---
id: nid_j54dq6ra33hix1e8aavanb8bz_E
title: "High level approach on how we are going to work with the agent"
status: open
deps: []
links: []
created_iso: 2026-03-07T14:51:08Z
status_updated_iso: 2026-03-07T14:51:08Z
type: task
priority: 3
assignee: nickolaykondratyev
---

# Chainsaw — High-Level Design (V1)

Codename: **CHAINSAW**. Package: `com.glassthought.chainsaw`.

## Context

Previously, a TOP_LEVEL_AGENT Claude session orchestrated sub-agents. Problem: sub-agent context
polluted the orchestrator's context window. Solution: a **Kotlin CLI harness** replaces the
orchestrator. Sub-agents are spawned as independent processes — their context is fully isolated.

## What the Harness Does

- Reads a task/ticket
- Orchestrates workflow phases (defined in **JSON**)
- Spawns code agents (Claude Code, Droid, etc.) via **TMUX**
- Runs a **local HTTP server** (Ktor CIO) — starts once, stays alive for entire harness process
- Manages file-based context (PUBLIC.md / PRIVATE.md / SHARED_CONTEXT.md)
- Handles git commits between phases
- Monitors convergence via timeout + ping mechanism (see Agent Health Monitoring)
- Uses `DirectLLMApi` for its own decisions (not everything is hardcoded Kotlin logic)

## CLI Entry Point

**picocli** for CLI parsing. V1 has a single subcommand:

```
chainsaw run --workflow <name> --ticket <path>
```

- `--ticket` **(required)**: path to a ticket markdown file. Chainsaw always operates on a ticket.
  - Ticket is a markdown file with YAML frontmatter containing at minimum an `id` field and `title` field.
  - The ticket `id` is used for branch naming and state tracking.
- `--workflow`: workflow definition name (e.g., `straightforward`, `with-planning`)

On startup, checks for existing `current_state.json`. If found, offers to resume from last checkpoint or start fresh.

---

## Sub-Agent Invocation — TMUX Only

All agents are spawned as **interactive TMUX sessions** via `TmuxSessionManager` + `TmuxCommunicator`.
`InteractiveProcessRunner` was a prototype — **remove it**.

- `CodeAgent` interface with `ClaudeCodeAgent` implementation
- Leverages subscription pricing; interface allows swapping agent implementations
- **Strictly serial** execution for V1 (1 harness → 1 TMUX session at a time)
- **Separate sessions per phase** — each phase spawns a fresh agent. Context carries via files.
- Future: parallel sessions on separate git worktrees (branch as identifier)

### CodeAgent Abstraction (rough)

```
CodeAgent.run(
    instructionFile: Path,       // Markdown file with full instructions
    workingDir: Path,
    publicOutputFile: Path,      // explicit PUBLIC.md path
    privateOutputFile: Path,     // explicit PRIVATE.md path
) -> AgentResult { exitCode, stdout }
```

- Instructions written to Markdown file (preserves formatting vs. prompt text)
- V1: no tool restrictions (allow everything)

---

## Agent↔Harness Communication — Bidirectional

### Architecture

```
┌────────────┐    HTTP (curl)     ┌──────────────────┐
│  Agent      │ ──────────────→  │  Harness Server   │
│  (in TMUX)  │                   │  (Ktor CIO)       │
│             │ ←──────────────  │                    │
│             │   TMUX send-keys  │                    │
└────────────┘                   └──────────────────┘
```

- **Agent → Harness**: HTTP POST via `harness-cli-for-agent.sh` (wraps curl)
- **Harness → Agent**: TMUX `send-keys` (only way to communicate with running agents)

### Server Port — File-Based Discovery

Server binds to **port 0** (OS-assigned). On startup, writes the assigned port to:
```
$HOME/.chainsaw_agent_harness/server/port.txt
```

- `harness-cli-for-agent.sh` reads this file to construct the server URL
- Single helper method in the CLI encapsulates URL construction
- On server shutdown, this file is **deleted**
- CLI errors out if file does not exist (server not running)
- **No env var needed** — eliminates port collision risk entirely

Server starts once at harness startup, stays alive across all phases.

### Agent CLI Script
<!-- ref.ap.8PB8nMd93D3jipEWhME5n.E -- implementation in scripts/harness-cli-for-agent.sh -->

**`harness-cli-for-agent.sh`** — bash script wrapping curl calls:
- Lives on `$PATH` of the started agent
- Reads port from `$HOME/.chainsaw_agent_harness/server/port.txt`
- Agent receives `--help` content in its instructions, wrapped in
  `<critical_to_keep_through_compaction>` tags to survive context compaction

### Structured Text Delivery — Temp File Pattern

All structured/formatted content sent to agents goes through temp files:
- Write content to `$HOME/.chainsaw_agent_harness/tmp/agent_comm/<unique_name>.md`
- Send file path to agent via TMUX `send-keys`: `"Read instructions at <path>"`
- Applies to: instruction files, Q&A answers, any multi-line content
- **Exception**: Simple single-line messages (e.g., Wingman GUID handshake) can be sent directly

### V1 Server Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /agent/done` | Agent completed its task. Harness kills TMUX session, proceeds to next phase. |
| `POST /agent/question` | Agent has a question. Curl blocks until human answers. Answer delivered via TMUX send-keys (temp file). |
| `POST /agent/failed` | Unrecoverable error. Triggers `FailedPhaseUseCase`. |
| `POST /agent/status` | Agent responds to health ping (see Agent Health Monitoring). |

All requests include the **git branch** as identifier (key for future parallelism).

### Q&A Flow — Attended Only (V1)

1. Agent calls `harness-cli-for-agent.sh question "How should I handle X?"`
2. CLI POSTs to `/agent/question` with branch + question text
3. Harness presents question to human (stdout/interactive)
4. Human answers (V1: human must be present; no autonomous fallback)
5. Harness writes answer to temp file (`$HOME/.chainsaw_agent_harness/tmp/agent_comm/`)
6. Harness sends file path to agent via TMUX `send-keys`
7. Harness responds 200 to the blocked curl (unblocking agent CLI script)
8. Agent reads temp file, continues

### Agent Lifecycle — New Session

1. Harness creates TMUX session
2. Harness starts agent (e.g., `claude`) in the TMUX session
3. Harness sends Wingman GUID handshake (plain text, directly via send-keys)
4. Wingman resolves session ID from GUID
5. Harness writes instruction file to temp file
6. Harness sends `"Read instructions at <path>"` via TMUX `send-keys`
7. Agent works, may call CLI for questions
8. Agent calls `harness-cli-for-agent.sh done` when finished
9. Harness receives `/agent/done`, kills TMUX session
10. Harness proceeds to next workflow phase

### Agent Lifecycle — Resume Session (after crash)

1. Harness already has session ID from Wingman (saved previously)
2. Harness starts agent with `claude --resume <session_id>` in new TMUX session
3. Skip GUID/Wingman step — session already known
4. Harness writes instruction file, sends path via TMUX send-keys
5. Flow continues as normal from step 7 above

---

## Agent Health Monitoring — UseCase Pattern

Timeout + ping mechanism to detect crashed/hung agents.

### Flow

1. **No callback timeout** (default: 30 min): If no `/agent/done`, `/agent/failed`, or `/agent/question` within configured timeout → triggers `NoStatusCallbackTimeOutUseCase`
2. **Ping**: Harness sends a message to agent via TMUX send-keys asking if it's still running and needs more time. Agent is expected to reply via `POST /agent/status`
3. **Ping timeout** (default: 3 min): If no `/agent/status` reply → triggers `NoReplyToPingUseCase`
4. **Crash handling**: Kill TMUX session → attempt to RESUME agent session using Wingman-saved session ID

### UseCase Classes

Each distinct state/scenario is encapsulated in its own `UseCase` class:

| UseCase | Trigger | Action |
|---|---|---|
| `NoStatusCallbackTimeOutUseCase` | No callback after X min | Ping agent via TMUX send-keys |
| `NoReplyToPingUseCase` | No `/agent/status` reply after Y min | Mark as CRASHED, kill TMUX, attempt resume |
| `FailedToExecutePlanUseCase` | Agent calls `/agent/failed` during plan execution | Spin up cleanup agent, enrich ticket, reset codebase, re-open ticket |

- **Simple encapsulated objects** — NOT a state machine pattern
- Live in their own namespace, separate from detailed implementation
- Each UseCase handles one well-defined scenario

### FailedToExecutePlanUseCase Detail

When plan execution hits blocking issues:
1. Spin up a **cleanup agent** (full write access — needs to run cleanup commands)
2. Cleanup agent analyzes the approach taken and why it failed
3. Writes failure summary + learnings into the ticket (so next retry is better informed)
4. Runs cleanup commands to restore codebase to starting state
5. Ticket re-opened via `tk reopen <id>`

---

## Session ID Tracking — Wingman

**Problem:** Claude Code doesn't expose its session ID to the agent itself.

**Solution:** `Wingman` interface + `ClaudeCodeWingman` implementation.

1. Harness generates a GUID for each new session
2. Harness sends GUID to agent as first message (plain text, directly): `"Here is a GUID: [$GUID]. We will use it to identify this session."`
3. `ClaudeCodeWingman` searches `$HOME/.claude/projects/.../*.jsonl` for files containing the GUID
4. Matched filename = session ID (e.g., `77d5b7ea-cf04-453b-8867-162404763e18.jsonl`)
5. Session ID stored in `.ai_out/${git_branch}/phases/${part}/${ROLE}/session_ids/${timestamp}.json`
6. Enables session resumption after crashes

**Not used during resume** — session ID already known from prior Wingman resolution.

---

## DirectLLMApi

For harness-internal quick tasks (compress ticket title, suggest feature name, evaluate iterations):

```
DirectLLMApi.askModel(prompt: String, tier: ModelTier): String

enum ModelTier { QuickCheap, Medium }
```

- Each `ModelTier` maps to a separate API provider (e.g., GLM for quick, GPT for medium)
- Provider is configurable per tier
- **Iteration decisions**: LLM returns structured JSON only (instructed to output raw JSON, no wrapping)

---

## Workflow Definition — Kotlin + JSON

Core engine in Kotlin; workflow phases defined in **JSON** under `./config/workflows/`.
JSON chosen because: 1) easy for LLMs to generate during planning phase, 2) strong tooling support.

**Serialization: Jackson + Kotlin module** throughout (workflow definitions, current_state.json, DirectLLMApi responses).

**Workflow resolution**: `--workflow <name>` → loads `./config/workflows/<name>.json`. Fail-fast if not found.

### Shared Schema — Parts

Both static and dynamic workflows use the same **parts** schema. One parser handles all workflows.

A **part** is a sequential unit of work with an ordered list of phases (typically implementor → reviewer) and an iteration config:

```json
{
  "parts": [
    {
      "name": "part_name",
      "description": "What this part accomplishes",
      "phases": [
        { "role": "SOME_IMPLEMENTOR" },
        { "role": "SOME_REVIEWER" }
      ],
      "iteration": { "max": 4 }
    }
  ]
}
```

- Parts execute **sequentially** (part_1 completes before part_2 starts)
- Within a part, phases execute sequentially (implementor → reviewer)
- Iteration loops back within the part (implementor ↔ reviewer) up to `max` times

### Straightforward Workflow (static parts)

```json
// config/workflows/straightforward.json
{
  "name": "straightforward",
  "parts": [
    {
      "name": "main",
      "description": "Implement and review",
      "phases": [
        { "role": "IMPLEMENTOR_WITH_SELF_PLAN" },
        { "role": "IMPLEMENTATION_REVIEWER" }
      ],
      "iteration": { "max": 4 }
    }
  ]
}
```

Single part, single iteration loop. No planning, no dynamic phase generation.

### With-Planning Workflow (planning loop + dynamic execution)

```json
// config/workflows/with-planning.json
{
  "name": "with-planning",
  "planningPhases": [
    { "role": "PLANNER" },
    { "role": "PLAN_REVIEWER" }
  ],
  "planningIteration": { "max": 3 },
  "executionPhasesFrom": "plan.json"
}
```

#### Startup Flow — With Planning

```
1. Load with-planning.json
2. PLANNING LOOP:
   a. Spawn PLANNER agent (explores codebase, asks questions via Q&A, writes plan)
      - Planner receives: ticket, role catalog, plan format instructions
      - Planner outputs: PLAN.md (human-readable) + plan.json (machine-readable)
   b. Spawn PLAN_REVIEWER agent (reviews the plan)
   c. LLM evaluates: plan approved? → exit loop / needs revision? → back to (a)
   d. Max 3 iterations
3. Harness parses plan.json → resolves into parts (same schema as straightforward)
4. Resolved parts stored in-memory + persisted in current_state.json (for resume)
5. EXECUTION: parts execute sequentially, each with its own iteration loop
6. If major blocking issues during execution → FailedToExecutePlanUseCase
```

#### Planner Output — Separate Files

```
.ai_out/${git_branch}/shared/plan/
├── PLAN.md        # Human-readable plan (rationale, approach, risks)
└── plan.json      # Machine-readable (parts schema, parsed by harness)
```

Example `plan.json`:
```json
{
  "parts": [
    {
      "name": "ui_design",
      "description": "Design the dashboard UI",
      "phases": [
        { "role": "UI_DESIGNER" },
        { "role": "UI_REVIEWER" }
      ],
      "iteration": { "max": 3 }
    },
    {
      "name": "backend_impl",
      "description": "Implement API endpoints",
      "phases": [
        { "role": "IMPLEMENTOR" },
        { "role": "SECURITY_REVIEWER" },
        { "role": "IMPLEMENTATION_REVIEWER" }
      ],
      "iteration": { "max": 4 }
    }
  ]
}
```

#### Role Catalog — Auto-Discovered

The planner needs to know what roles are available. The catalog is **auto-discovered** from `$CHAINSAW_AGENTS_DIR`:

- Every Markdown file in `$CHAINSAW_AGENTS_DIR` is an eligible role (no opt-in flag needed)
- Extract `description` (required) and `description_long` (optional) from YAML frontmatter
- Present the catalog to the planner agent in its instructions

Example role file frontmatter:
```yaml
---
description: "Reviews code for security vulnerabilities and OWASP top 10"
description_long: "Performs deep security analysis including auth flows, input validation, SQL injection, XSS..."
---
```

#### Plan Mutability During Execution

- **Minor adjustments OK**: Implementor can adjust approach within a part and keep going
- **Major deviations → fail explicitly**: If blocking issues or fundamental approach changes are needed, agent calls `/agent/failed` → `FailedToExecutePlanUseCase` (cleanup, enrich ticket, re-open for retry)
- Do NOT attempt to patch the plan mid-execution

### Startup Flow — Straightforward (No Planning)

```
1. Load straightforward.json
2. Parse parts (static, defined in JSON)
3. Parts stored in-memory + persisted in current_state.json
4. EXECUTION: parts execute sequentially, each with its own iteration loop
```

### Phase Transitions — Hybrid

- **Automatic** for straightforward transitions (implementor done → reviewer starts)
- **LLM-evaluated** for iteration decisions: DirectLLMApi receives reviewer's PUBLIC.md + reviewed role's PUBLIC.md + SHARED_CONTEXT.md, returns structured JSON (pass/fail + reason)

### Context Assembly — ContextProvider

A `ContextProvider` interface is responsible for assembling context packages for:
- Iteration decision prompts (what the LLM sees)
- Agent instruction files (what gets concatenated)
- Planner instructions (ticket + role catalog + format instructions)
- Easy to adjust and clear to see what is being shared

---

## File Structure

```
.ai_out/${git_branch}/
├── harness_private/
│   ├── current_state.json                              # Serialized workflow state (incl. resolved parts); enables resume
│   └── PRIVATE.md                                      # Harness internal context (if needed)
├── shared/
│   ├── SHARED_CONTEXT.md                               # Cross-cutting context for ALL agents (agents can modify)
│   ├── LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt  # Links to all PUBLIC.md files
│   └── plan/
│       ├── PLAN.md                                     # Human-readable plan (with-planning only)
│       └── plan.json                                   # Machine-readable plan (with-planning only)
├── planning/                                           # Plan-making loop (with-planning only)
│   ├── PLANNER/
│   │   ├── PUBLIC.md
│   │   ├── PRIVATE.md
│   │   └── session_ids/${timestamp}.json
│   └── PLAN_REVIEWER/
│       ├── PUBLIC.md
│       ├── PRIVATE.md
│       └── session_ids/${timestamp}.json
├── phases/                                             # Plan execution
│   ├── part_1/
│   │   ├── ${ROLE}/
│   │   │   ├── PUBLIC.md
│   │   │   ├── PRIVATE.md
│   │   │   └── session_ids/${timestamp}.json           # Session ID + agent type
│   │   └── ${ROLE_2}/
│   │       └── ...
│   └── part_2/
│       └── ...
```

**Temp files for agent communication:**
```
$HOME/.chainsaw_agent_harness/tmp/agent_comm/           # Temp instruction/answer files sent to agents
```

**Server port file:**
```
$HOME/.chainsaw_agent_harness/server/port.txt           # Written on startup, deleted on shutdown
```

### Iteration within a part

When review fails and iteration loops back, the **same** `phases/part_N/{ROLE}/` directory is reused. The agent reads its own prior PUBLIC.md for context on what to fix.

### Iteration within planning

Same pattern: `planning/PLANNER/` and `planning/PLAN_REVIEWER/` directories are reused across planning iterations. Each iteration overwrites PUBLIC.md with updated plan content.

## Agent Role Definitions

- Each ROLE has a corresponding Markdown file in `$CHAINSAW_AGENTS_DIR`
- **Fail-fast on startup** if role file is missing
- Instruction file is a **concatenation** of:
  - Role definition file
  - Ticket content
  - `SHARED_CONTEXT.md`
  - `LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt`
  - Phase-specific artifacts (e.g., reviewer's PUBLIC.md from prior iteration)
  - `harness-cli-for-agent.sh --help` content (in `<critical_to_keep_through_compaction>` tags)

## Git Branch / Feature Naming

Branch is derived from the ticket. Format: `{TICKET_ID}__{slugified_title}__try-{N}`

- `TICKET_ID`: the `id` field from the ticket's YAML frontmatter
- `slugified_title`: the ticket `title` slugified (lowercase, hyphens); compressed via `DirectLLMApi(QuickCheap)` if too long
- `try-{N}`: starts at 1, incremented on each retry after `FailedToExecutePlanUseCase` resets and re-opens
- Delimiter between components: `__` (double underscore)

## Harness-Level Resume

- `current_state.json` tracks which phase/part the workflow is currently in
- On `chainsaw run`, if `current_state.json` exists for the given ticket+branch, offer to resume
- Resume skips completed phases, picks up from the last in-progress phase
- More important than individual agent resume (which is deferred to V2)

---

## Key Technology Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Workflow format | **JSON** | Easy for LLMs to generate; strong tooling |
| Workflow schema | **Shared "parts" structure** | Same schema for static workflows and planner output; one parser |
| JSON library | **Jackson + Kotlin module** | Battle-tested, runtime reflection |
| CLI parser | **picocli** | Mature, annotation-driven |
| HTTP server | **Ktor CIO** | Coroutine-native, Kotlin ecosystem |
| Server port | **OS-assigned (port 0)** | Written to file; CLI reads file; no env var; no collisions |
| Session tracking | **Wingman interface** | `ClaudeCodeWingman` impl; abstracted for future agent types |
| Package | **com.glassthought.chainsaw** | Chainsaw as sub-package under glassthought |
| Q&A mode | **Attended only (V1)** | Human must be at terminal |
| Cleanup agent | **Full write access** | Runs cleanup commands, enriches ticket, restores starting state |
| Role catalog | **Auto-discovered from `$CHAINSAW_AGENTS_DIR`** | Every .md file is eligible; `description` from frontmatter |
| Plan review | **Agent-based (PLAN_REVIEWER)** | Same iteration pattern as other review phases |
| Plan output | **Separate files** | PLAN.md (human) + plan.json (machine) in `shared/plan/` |
| Plan mutability | **Frozen; minor tweaks OK** | Major deviations → fail explicitly via FailedToExecutePlanUseCase |

## Cleanup Items

- **Remove `InteractiveProcessRunner`** — prototype, TMUX is the only path
- **Move classes from `org.example`** to `com.glassthought.chainsaw`

---

## V1 Scope Summary

1. CLI: `chainsaw run --workflow <name> --ticket <path>` (picocli)
2. TMUX-based agent invocation (`CodeAgent` interface, `ClaudeCodeAgent` impl)
3. Ktor CIO HTTP server (port 0, file-based discovery) for agent→harness callbacks (done/question/failed/status)
4. Bash CLI script (`harness-cli-for-agent.sh`) for agents to call back (reads port from file)
5. Wingman interface + ClaudeCodeWingman for session ID discovery
6. File-based cross-agent context — phase-oriented directory structure under `.ai_out/${git_branch}/`
7. Temp file pattern for all structured content delivery to agents
8. JSON workflow definitions with shared "parts" schema (`straightforward` static; `with-planning` dynamic)
9. With-planning: PLANNER → PLAN_REVIEWER iteration loop → dynamic execution phases from plan.json
10. Role catalog auto-discovered from `$CHAINSAW_AGENTS_DIR` (every .md file is eligible; `description` from frontmatter)
11. ContextProvider interface for assembling context packages (incl. planner instructions with role catalog)
12. Hybrid phase transitions (automatic + LLM-evaluated via DirectLLMApi returning structured JSON)
13. Agent health monitoring: timeout → ping → crash detection (UseCase pattern)
14. FailedToExecutePlanUseCase: cleanup agent (write access) → enrich ticket → restore starting state → re-open ticket
15. DirectLLMApi for harness decisions (GLM QuickCheap tier first)
16. Git commits between phases; branch naming with try-N for retries
17. `current_state.json` for harness-level resume (Jackson serialization, incl. resolved parts)
18. Strictly serial execution (1 harness → 1 agent at a time)
19. Separate sessions per phase (individual agent resume deferred to V2)

---

## Linked Documentation

This high-level plan is mirrored in `ai_input/memory/auto_load/1_core_description.md` (auto-loaded into every agent's context).
**If this plan changes, update that file explicitly to keep sub-agents aligned.**
