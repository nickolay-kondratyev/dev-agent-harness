# Chainsaw — High-Level Design (V1)

Codename: **CHAINSAW**. Package: `com.glassthought.chainsaw`.

## Context

Previously, a TOP_LEVEL_AGENT Claude session orchestrated sub-agents. Problem: sub-agent context
polluted the orchestrator's context window. Solution: a **Kotlin CLI harness** replaces the
orchestrator. Sub-agents are spawned as independent processes — their context is fully isolated.

## What the Harness Does

- Reads a task/ticket
- Orchestrates workflow sub-parts (defined in **JSON**)
- Spawns code agents (Claude Code, Droid, etc.) via **TMUX**
- Runs a **local HTTP server** (Ktor CIO) — starts once, stays alive for entire harness process
- Manages file-based context (PUBLIC.md / SHARED_CONTEXT.md)
- Handles git commits between sub-parts
- Monitors convergence via timeout + ping mechanism (see Agent Health Monitoring)
- Uses `DirectLLMApi` for its own decisions (not everything is hardcoded Kotlin logic)

## CLI Entry Point

ap.mmcagXtg6ulznKYYNKlNP.E

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

- `CodeAgent` interface with `ClaudeCodeAgent` implementation
- Leverages subscription pricing; interface allows swapping agent implementations
- **Strictly serial** execution for V1 (1 harness → 1 TMUX session at a time)
- **Separate sessions per sub-part** — each run spawns a fresh agent. Context carries via files.
- Future: parallel sessions on separate git worktrees (branch as identifier)

### CodeAgent Abstraction

```
CodeAgent.run(
    instructionFile: Path,       // Markdown file with full instructions
    workingDir: Path,
    publicOutputFile: Path,      // explicit PUBLIC.md path
) -> AgentResult { exitCode, stdout }
```

- Instructions written to Markdown file (preserves formatting vs. prompt text)
- V1: no tool restrictions (allow everything)

---

## Agent↔Harness Communication — Bidirectional
<!-- ref.ap.NAVMACFCbnE7L6Geutwyk.E — HarnessServer implementation -->

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
- On server shutdown, this file is **deleted**
- **No env var needed** — eliminates port collision risk entirely

Server starts once at harness startup, stays alive across all sub-parts.

### Agent CLI Script

> **Removed**: `scripts/harness-cli-for-agent.sh` and `scripts/test_harness_cli.sh` have been
> deleted. A replacement CLI script will be built per the updated spec.

### Structured Text Delivery — Temp File Pattern

All structured/formatted content sent to agents goes through temp files:
- Write content to `$HOME/.chainsaw_agent_harness/tmp/agent_comm/<unique_name>.md`
- Send file path to agent via TMUX `send-keys`: `"Read instructions at <path>"`
- **Exception**: Simple single-line messages (e.g., AgentSessionIdResolver GUID handshake) can be sent directly

### V1 Server Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /agent/done` | Agent completed its task. Harness kills TMUX session, proceeds to next sub-part. |
| `POST /agent/question` | Agent has a question. Curl blocks until human answers. Answer delivered via TMUX send-keys (temp file). |
| `POST /agent/failed` | Unrecoverable error. Triggers `FailedToExecutePlanUseCase`. |
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
3. Harness sends AgentSessionIdResolver GUID handshake (plain text, directly via send-keys)
4. AgentSessionIdResolver resolves session ID from GUID
5. Harness writes instruction file to temp file
6. Harness sends `"Read instructions at <path>"` via TMUX `send-keys`
7. Agent works, may call CLI for questions
8. Agent calls `harness-cli-for-agent.sh done` when finished
9. Harness receives `/agent/done`, kills TMUX session
10. Harness proceeds to next sub-part

### Agent Lifecycle — Resume Session (after crash)

1. Harness already has session ID (last entry in `sessionIds` array in `current_state.json`)
2. Harness starts agent with `claude --resume <session_id>` in new TMUX session
3. Skip GUID/AgentSessionIdResolver step — session already known
4. Harness writes instruction file, sends path via TMUX send-keys
5. Flow continues as normal from step 7 above

---

## Agent Health Monitoring — UseCase Pattern

Timeout + ping mechanism to detect crashed/hung agents.

### Flow

1. **No callback timeout** (default: 30 min): If no `/agent/done`, `/agent/failed`, or `/agent/question` within configured timeout → triggers `NoStatusCallbackTimeOutUseCase`
2. **Ping**: Harness sends a message to agent via TMUX send-keys asking if it's still running and needs more time. Agent is expected to reply via `POST /agent/status`
3. **Ping timeout** (default: 3 min): If no `/agent/status` reply → triggers `NoReplyToPingUseCase`
4. **Crash handling**: Kill TMUX session → attempt to RESUME agent session using last `sessionIds` entry

### UseCase Classes

Each distinct state/scenario is encapsulated in its own `UseCase` class:

| UseCase | Trigger | Action |
|---|---|---|
| `NoStatusCallbackTimeOutUseCase` | No callback after X min | Ping agent via TMUX send-keys |
| `NoReplyToPingUseCase` | No `/agent/status` reply after Y min | Mark as CRASHED, kill TMUX, attempt resume |
| `FailedToExecutePlanUseCase` | Agent calls `/agent/failed` during plan execution | Spin up cleanup agent, enrich ticket, reset codebase, re-open ticket |

- **Simple encapsulated objects** — NOT a state machine pattern
- Each UseCase handles one well-defined scenario

### FailedToExecutePlanUseCase Detail

When plan execution hits blocking issues:
1. Spin up a **cleanup agent** (full write access — needs to run cleanup commands)
2. Cleanup agent analyzes the approach taken and why it failed
3. Writes failure summary + learnings into the ticket (so next retry is better informed)
4. Runs cleanup commands to restore codebase to starting state
5. Ticket re-opened via `tk reopen <id>`

---

## Session ID Tracking — AgentSessionIdResolver
<!-- ref.ap.gCgRdmWd9eTGXPbHJvyxI.E -->

**Problem:** Claude Code doesn't expose its session ID to the agent itself.

**Solution:** `AgentSessionIdResolver` interface + `ClaudeCodeAgentSessionIdResolver` implementation.

1. Harness generates a GUID for each new session
2. Harness sends GUID to agent as first message (plain text, directly): `"Here is a GUID: [$GUID]. We will use it to identify this session."`
3. `ClaudeCodeAgentSessionIdResolver` searches `$HOME/.claude/projects/.../*.jsonl` for files containing the GUID
4. Matched filename = session ID (e.g., `77d5b7ea-cf04-453b-8867-162404763e18.jsonl`)
5. Session ID stored in `current_state.json` under the sub-part's `sessionIds` array
6. Enables session resumption after crashes

**Not used during resume** — session ID already known from prior AgentSessionIdResolver resolution.

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

## Workflow Definition, File Structure, and Iteration Semantics

See [`doc/ai-out-schema.md`](ai-out-schema.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) for:
- Unified parts/sub-parts schema (used by static workflows **and** planner-generated plans)
- `plan.json` / `current_state.json` lifecycle
- Iteration semantics (sub-part-level iteration with `loopsBackTo`)
- `.ai_out/` directory tree and scoping rules
- Cross-agent visibility via ContextProvider
- Session ID storage in `current_state.json`

**Key points:**
- **JSON** under `./config/workflows/`. **Jackson + Kotlin module** for serialization.
- `--workflow <name>` → loads `./config/workflows/<name>.json`. Fail-fast if not found.
- Two modes: **straightforward** (static parts) and **with-planning** (planning loop → dynamic execution).
- `current_state.json` in `harness_private/` is the single source of truth for plan + progress + session IDs.

### Sub-Part Transitions — Hybrid

- **Automatic** for straightforward transitions (implementor done → reviewer starts)
- **LLM-evaluated** for iteration decisions: DirectLLMApi receives reviewer's PUBLIC.md + implementor's PUBLIC.md + SHARED_CONTEXT.md, returns structured JSON (pass/fail + reason)

### Context Assembly — ContextProvider

A `ContextProvider` interface is responsible for assembling context packages for:
- Iteration decision prompts (what the LLM sees)
- Agent instruction files (what gets concatenated)
- Planner instructions (ticket + role catalog + format instructions)
- PLAN_REVIEWER instructions (includes `plan.json` from `harness_private/`)

### Role Catalog — Auto-Discovered
<!-- ref.ap.iF4zXT5FUcqOzclp5JVHj.E -->

- Every Markdown file in `$CHAINSAW_AGENTS_DIR` is an eligible role
- Extract `description` (required) and `description_long` (optional) from YAML frontmatter
- **Fail-fast on startup** if a role referenced in the workflow is missing

### Plan Mutability During Execution

- **Minor adjustments OK**: Implementor can adjust approach within a part
- **Major deviations → fail explicitly**: Agent calls `/agent/failed` → `FailedToExecutePlanUseCase`
- Do NOT attempt to patch the plan mid-execution

---

## Git Branch / Feature Naming
<!-- ref.ap.THL21SyZzJhzInG2m4zl2.E -->

Branch is derived from the ticket. Format: `{TICKET_ID}__{slugified_title}__try-{N}`

- `TICKET_ID`: the `id` field from the ticket's YAML frontmatter
- `slugified_title`: the ticket `title` slugified (lowercase, hyphens); compressed via `DirectLLMApi(QuickCheap)` if too long
- `try-{N}`: starts at 1, incremented on each retry after `FailedToExecutePlanUseCase` resets and re-opens
- Delimiter between components: `__` (double underscore)

## Harness-Level Resume

- `current_state.json` tracks which part/sub-part the workflow is currently in, plus session IDs
- On `chainsaw run`, if `current_state.json` exists for the given ticket+branch, offer to resume
- Resume skips completed sub-parts, picks up from the last in-progress sub-part

---

## Key Technology Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Workflow format | **JSON** | Easy for LLMs to generate; strong tooling |
| Workflow schema | **Unified parts/sub-parts** | Same schema for static workflows and planner output; one parser |
| JSON library | **Jackson + Kotlin module** | Battle-tested, runtime reflection |
| CLI parser | **picocli** | Mature, annotation-driven |
| HTTP server | **Ktor CIO** | Coroutine-native, Kotlin ecosystem |
| Server port | **OS-assigned (port 0)** | Written to file; CLI reads file; no env var; no collisions |
| Session tracking | **AgentSessionIdResolver interface** | `ClaudeCodeAgentSessionIdResolver` impl; abstracted for future agent types |
| Session storage | **`sessionIds` array in `current_state.json`** | All state in one file; last element = resumable |
| Package | **com.glassthought.chainsaw** | Chainsaw as sub-package under glassthought |
| Q&A mode | **Attended only (V1)** | Human must be at terminal |
| Role catalog | **Auto-discovered from `$CHAINSAW_AGENTS_DIR`** | Every .md file is eligible; `description` from frontmatter |
| Plan mutability | **Frozen; minor tweaks OK** | Major deviations → fail explicitly via FailedToExecutePlanUseCase |

---

## Linked Documentation

| Doc | Content |
|-----|---------|
| [`doc/ai-out-schema.md`](ai-out-schema.md) | Directory schema, unified parts/sub-parts schema, iteration semantics, session IDs |
| `ai_input/memory/auto_load/1_core_description.md` | Auto-loaded summary for sub-agents — **update if this doc changes** |
