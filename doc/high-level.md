# Shepherd — High-Level Design (V1)

Codename: **TICKET_SHEPHERD**. Package: `com.glassthought.shepherd`.

## Vocabulary

| Term | Definition |
|------|------------|
| **Ticket** | A markdown file with YAML frontmatter (`id`, `title`). The mandatory starting point for every Shepherd run — defines what needs to be done. Used for branch naming, state tracking, and agent context. |
| **ShepherdServer** (aka Server) | The long-lived HTTP server instance that starts at harness launch and handles all requests from agents. One per harness process. |
| **Agent** | An instance of a code agent (e.g., Claude Code, PI) running in a TMUX session. In the future, multiple agents may be alive simultaneously. |
| **HandshakeGuid** | A harness-generated identifier (`handshake.${UUID}`) assigned to each agent session. Used in all agent↔server communication. See [protocol doc](core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E). |
| **Orchestration Loop** | The harness-side logic that reads the workflow JSON, iterates through parts/sub-parts, spawns agents, evaluates iteration decisions, and manages state. Not an agent — a Kotlin process. |

---

## Hard Constraints

- **One TMUX session per sub-part.** A sub-part gets exactly one TMUX session, spawned on first run and kept alive across iteration loops. The session is killed only when the **part** completes. No kill/respawn between iterations.
- **At most 2 sub-parts per part.** First sub-part is the doer (implementor/planner). Optional second sub-part is the reviewer. On review failure, the harness loops back to the doer. This keeps part execution trivially simple.
  - In V2 we may relax this but in V1 this is KISS constraint.

---

## Context

Previously, a TOP_LEVEL_AGENT Claude session orchestrated sub-agents. Problem: sub-agent context
polluted the orchestrator's context window. Solution: a **Kotlin CLI harness** replaces the
orchestrator. Sub-agents are spawned as independent processes — their context is fully isolated.

## What the Harness Does

**Ticket-driven**: A ticket is the mandatory starting point for every Shepherd run. The ticket
defines what needs to be done; the workflow defines how. Without a ticket, Shepherd does not run.

[`TicketShepherd`](core/TicketShepherd.md) (ref.ap.P3po8Obvcjw4IXsSUSU91.E) is the central
coordinator that drives the entire workflow. It:

- Walks through parts and sub-parts defined in the workflow JSON
- Calls `SpawnTmuxAgentSessionUseCase` to spawn agents in TMUX sessions
- Receives callbacks from the server when agents signal done/failed/question
- Evaluates iteration decisions via `DirectLLMApi` (loop back or move on)
- Manages file-based context (PUBLIC.md / SHARED_CONTEXT.md)
- Handles git commits between sub-parts
- Monitors agent health via timeout + ping mechanism (see Agent Health Monitoring)

The harness also runs a **local HTTP server** (Ktor CIO) — starts once, stays alive for
the entire harness process. The server routes agent callbacks to `TicketShepherd`.

## CLI Entry Point

ap.mmcagXtg6ulznKYYNKlNP.E

**picocli** for CLI parsing. V1 has a single subcommand:

```
shepherd run --workflow <name> --ticket <path>
```

- `--ticket` **(required)**: path to a ticket markdown file. Shepherd always operates on a ticket.
  - Ticket is a markdown file with YAML frontmatter containing at minimum an `id` field and `title` field.
  - The ticket `id` is used for branch naming and state tracking.
- `--workflow`: workflow definition name (e.g., `straightforward`, `with-planning`)

On startup, the CLI uses [`TicketShepherdCreator`](core/TicketShepherdCreator.md)
(ref.ap.cJbeC4udcM3J8UFoWXfGh.E) to wire all dependencies and create a `TicketShepherd`.
If an existing `current_state.json` is found, offers to resume from last checkpoint or start fresh.

---

## Sub-Agent Invocation — TMUX Only

All agents are spawned as **interactive TMUX sessions** via `TmuxSessionManager` + `TmuxCommunicator`.

- `CodeAgent` interface with `ClaudeCodeAgent` implementation
- Leverages subscription pricing; interface allows swapping agent implementations
- **Strictly serial** execution for V1 (1 harness → 1 TMUX session at a time)
- **One TMUX session per sub-part** — kept alive across iteration loops (see Hard Constraints). New instructions delivered via `send-keys`.
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

Communication between agents and the harness is bidirectional through two distinct channels:
**Agent → Harness** via HTTP POST (curl wrapped by `harness-cli-for-agent.sh`), and
**Harness → Agent** via TMUX `send-keys` (the only way to push content to a running agent).
The harness runs a Ktor CIO server (port 0, OS-assigned) that stays alive for the entire run.

See [Agent-to-Server Communication Protocol](core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the full protocol specification: endpoints, payloads, HandshakeGuid identity, port discovery, Q&A flow, and agent CLI script.

### Agent Lifecycle — Spawn & Resume

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for:
- New session spawn flow (GUID generation, env var export, session ID resolution)
- Resume flow (reuse HandshakeGuid, skip session ID resolution)
- Session schema in `current_state.json`
- Agent CLI spec

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

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for full details
on HandshakeGuid, AgentSessionIdResolver, and session schema.

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

See:
- [`doc/schema/ai-out-directory.md`](schema/ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) — `.ai_out/` directory tree, scoping rules, cross-agent visibility via ContextProvider
- [`doc/schema/plan-and-current-state.md`](schema/plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E) — unified parts/sub-parts schema, `plan.json` / `current_state.json` lifecycle, iteration semantics, session ID storage

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

- Every Markdown file in `$TICKET_SHEPHERD_AGENTS_DIR` is an eligible role
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
- On `shepherd run`, if `current_state.json` exists for the given ticket+branch, offer to resume
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
| Package | **com.glassthought.shepherd** | Shepherd as sub-package under glassthought |
| Q&A mode | **Attended only (V1)** | Human must be at terminal |
| Role catalog | **Auto-discovered from `$TICKET_SHEPHERD_AGENTS_DIR`** | Every .md file is eligible; `description` from frontmatter |
| Plan mutability | **Frozen; minor tweaks OK** | Major deviations → fail explicitly via FailedToExecutePlanUseCase |

---

## Linked Documentation

| Doc | Content |
|-----|---------|
| [`doc/schema/ai-out-directory.md`](schema/ai-out-directory.md) | `.ai_out/` directory tree, scoping rules, cross-agent visibility |
| [`doc/schema/plan-and-current-state.md`](schema/plan-and-current-state.md) | Unified parts/sub-parts schema, iteration semantics, session IDs, plan lifecycle |
| [`doc/core/agent-to-server-communication-protocol.md`](core/agent-to-server-communication-protocol.md) | Full agent↔server protocol — HandshakeGuid, endpoints, payloads, port discovery, Q&A flow, CLI script |
| [`doc/core/TicketShepherd.md`](core/TicketShepherd.md) | Central coordinator — owns SessionsState, receives server callbacks, drives iteration decisions |
| [`doc/core/TicketShepherdCreator.md`](core/TicketShepherdCreator.md) | Wires all dependencies, creates a ready-to-go TicketShepherd for a single run |
| [`doc/use-case/SpawnTmuxAgentSessionUseCase.md`](use-case/SpawnTmuxAgentSessionUseCase.md) | Agent spawn/resume flow, HandshakeGuid, callback contract, session schema, CLI spec |
| `ai_input/memory/auto_load/1_core_description.md` | Auto-loaded summary for sub-agents — **update if this doc changes** |
