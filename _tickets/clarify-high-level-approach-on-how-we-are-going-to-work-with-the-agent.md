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

- `--workflow`: workflow definition name (e.g., `straightforward`, `with-planning`)
- `--ticket`: path to the ticket markdown file

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

### Straightforward Workflow (static phases)

```json
{
  "name": "straightforward",
  "phases": [
    { "name": "IMPLEMENTATION", "role": "IMPLEMENTOR_WITH_SELF_PLAN", "mode": "read-write" },
    { "name": "REVIEW", "role": "IMPLEMENTATION_REVIEWER", "mode": "read-only", "dependsOn": "IMPLEMENTATION" }
  ],
  "iteration": { "over": ["IMPLEMENTATION", "REVIEW"], "max": 4 }
}
```

Single implementation part, single review. No dynamic phase generation.

### With-Planning Workflow (dynamic phases)

When `--workflow with-planning` is used:

1. **Planning phase** runs first: planner agent performs thorough exploration, clarification, then outputs `PLAN.md` with a structured JSON block defining parts and their roles
2. Harness parses the plan's JSON into a dynamic phase sequence
3. Phases execute per the plan:

```
phases/
├── part_1/
│   ├── UI_DESIGNER/       # e.g., mockups
│   └── UI_REVIEWER/
├── part_2/
│   ├── STANDARD_IMPLEMENTOR/
│   └── STANDARD_REVIEWER/
```

4. If blocking issues found during plan execution → `FailedToExecutePlanUseCase`

### Phase Transitions — Hybrid

- **Automatic** for straightforward transitions (IMPLEMENTATION done → REVIEW starts)
- **LLM-evaluated** for iteration decisions: DirectLLMApi receives reviewer's PUBLIC.md + reviewed role's PUBLIC.md + SHARED_CONTEXT.md, returns structured JSON (pass/fail + reason)

### Context Assembly — ContextProvider

A `ContextProvider` interface is responsible for assembling context packages for:
- Iteration decision prompts (what the LLM sees)
- Agent instruction files (what gets concatenated)
- Easy to adjust and clear to see what is being shared

---

## File Structure

```
.ai_out/${git_branch}/
├── harness_private/
│   ├── current_state.json                              # Serialized workflow state; enables harness-level resume
│   └── PRIVATE.md                                      # Harness internal context (if needed)
├── shared/
│   ├── SHARED_CONTEXT.md                               # Cross-cutting context for ALL agents (agents can modify)
│   ├── LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt  # Links to all PUBLIC.md files
│   └── plan/
│       └── PLAN.md                                     # High-level plan (with-planning workflow only)
├── phases/
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

## Agent Role Definitions

- Each ROLE has a corresponding Markdown file in `${AGENTS_DIR}/`
- **Fail-fast on startup** if role file is missing
- Instruction file is a **concatenation** of:
  - Role definition file
  - Ticket content
  - `SHARED_CONTEXT.md`
  - `LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt`
  - Phase-specific artifacts (e.g., reviewer's PUBLIC.md from prior iteration)
  - `harness-cli-for-agent.sh --help` content (in `<critical_to_keep_through_compaction>` tags)

## Git Branch / Feature Naming

Branch format: `{TICKET_ID}__{slugified_title}__try-{N}`

- `TICKET_ID`: from the ticket being worked
- `slugified_title`: compressed via `DirectLLMApi(QuickCheap)` if long
- `try-{N}`: incremented on each retry after `FailedToExecutePlanUseCase` resets and re-opens

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
| JSON library | **Jackson + Kotlin module** | Battle-tested, runtime reflection |
| CLI parser | **picocli** | Mature, annotation-driven |
| HTTP server | **Ktor CIO** | Coroutine-native, Kotlin ecosystem |
| Server port | **OS-assigned (port 0)** | Written to file; CLI reads file; no env var; no collisions |
| Session tracking | **Wingman interface** | `ClaudeCodeWingman` impl; abstracted for future agent types |
| Package | **com.glassthought.chainsaw** | Chainsaw as sub-package under glassthought |
| Q&A mode | **Attended only (V1)** | Human must be at terminal |
| Cleanup agent | **Full write access** | Runs cleanup commands, enriches ticket, restores starting state |

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
8. JSON workflow definitions (`straightforward` static; `with-planning` dynamic)
9. ContextProvider interface for assembling context packages
10. Hybrid phase transitions (automatic + LLM-evaluated via DirectLLMApi returning structured JSON)
11. Agent health monitoring: timeout → ping → crash detection (UseCase pattern)
12. FailedToExecutePlanUseCase: cleanup agent (write access) → enrich ticket → restore starting state → re-open ticket
13. DirectLLMApi for harness decisions (GLM QuickCheap tier first)
14. Git commits between phases; branch naming with try-N for retries
15. `current_state.json` for harness-level resume (Jackson serialization)
16. Strictly serial execution (1 harness → 1 agent at a time)
17. Separate sessions per phase (individual agent resume deferred to V2)
