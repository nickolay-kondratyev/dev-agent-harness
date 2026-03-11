# Shepherd — High-Level Design (V1)

> **This document and its linked `doc/` files are the authoritative specification.**
> When code diverges from these docs, the docs are correct and the code needs updating.
> Tickets and older notes are context — not spec. If a decision is not captured here, it is not decided.

Codename: **TICKET_SHEPHERD**. Package: `com.glassthought.shepherd`.

## Vocabulary

| Term | Definition |
|------|------------|
| **Ticket** | A markdown file with YAML frontmatter (`id`, `title`). The mandatory starting point for every Shepherd run — defines what needs to be done. Used for branch naming, state tracking, and agent context. |
| **ShepherdServer** (aka Server) | The long-lived HTTP server instance that starts at harness launch and handles all requests from agents. One per harness process. |
| **Agent** | An instance of a code agent (e.g., Claude Code, PI) running in a TMUX session. In the future, multiple agents may be alive simultaneously. |
| **HandshakeGuid** | A harness-generated identifier (`handshake.${UUID}`) assigned to each agent session. Used in all agent↔server communication. See [protocol doc](core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E). |
| **Orchestration Loop** | The harness-side logic that reads the workflow JSON, iterates through parts, and delegates each part to a `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E). The executor owns spawn/wait/iterate for its part. Server callbacks wake executors via `CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E). Not an agent — a Kotlin process. |

---

## Hard Constraints

- **One TMUX session per sub-part.** A sub-part gets exactly one TMUX session, spawned on first run and kept alive across iteration loops. The session is killed only when the **part** completes. No kill/respawn between iterations.
- **At most 2 sub-parts per part.** First sub-part is the doer (implementor/planner). Optional second sub-part is the reviewer. On review `needs_iteration`, the harness loops back to the doer. This keeps part execution trivially simple.
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

- Sets up the plan via `SetupPlanUseCase` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) — runs planning executor if needed
- Creates a [`PartExecutor`](core/PartExecutor.md) (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) for each part and calls `execute()`
- Delegates the agent spawn → wait → iterate cycle to the executor (no inline iteration loop)
- Server callbacks wake executors via `CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E)
- Manages file-based context (PUBLIC.md / SHARED_CONTEXT.md)
- Handles git commits between parts
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
**Agent → Harness** via HTTP POST (`callback_shepherd.*.sh` scripts wrapping curl), and
**Harness → Agent** via TMUX `send-keys` (the only way to push content to a running agent).
The harness runs a Ktor CIO server (port 0, OS-assigned) that stays alive for the entire run.

**All HTTP callbacks are non-blocking** — every callback script expects 200 and returns immediately.
When the harness needs to deliver content back to the agent (Q&A answers, iteration instructions),
it uses TMUX `send-keys`. No long-lived HTTP connections.

See [Agent-to-Server Communication Protocol](core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the full protocol specification: endpoints, payloads, HandshakeGuid identity, port discovery, user-question flow, and callback scripts.

### Agent Lifecycle — Spawn & Resume

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for:
- New session spawn flow (GUID generation, env var export, session ID resolution)
- Resume flow (reuse HandshakeGuid, skip session ID resolution)
- Session schema in `current_state.json`
- Callback script spec

---

## Agent Health Monitoring — UseCase Pattern

Timeout + ping mechanism to detect crashed/hung agents.

### Flow

1. **No callback timeout** (default: 30 min): If no `/callback-shepherd/done`, `/callback-shepherd/fail-workflow`, or `/callback-shepherd/user-question` within configured timeout → triggers `NoStatusCallbackTimeOutUseCase`
2. **Ping**: Harness sends a message to agent via TMUX send-keys asking if it's still running and needs more time. Agent is expected to reply via `callback_shepherd.ping-ack.sh`
3. **Ping timeout** (default: 3 min): If no `/callback-shepherd/ping-ack` reply → triggers `NoReplyToPingUseCase`
4. **Crash handling**: Kill TMUX session → attempt to RESUME agent session using last `sessionIds` entry

### UseCase Classes

Each distinct state/scenario is encapsulated in its own `UseCase` class:

| UseCase | Trigger | Action |
|---|---|---|
| `NoStatusCallbackTimeOutUseCase` | No callback after X min | Ping agent via TMUX send-keys |
| `NoReplyToPingUseCase` | No `/callback-shepherd/ping-ack` reply after Y min | Mark as CRASHED, kill TMUX, attempt resume |
| `FailedToExecutePlanUseCase` | Agent calls `/callback-shepherd/fail-workflow` during plan execution | Spin up cleanup agent, enrich ticket, reset codebase, re-open ticket |
| `FailedToConvergeUseCase` | Reviewer sends `needs_iteration` beyond `iteration.max` | Summarize state via BudgetHigh DirectLLM, present to user, user decides whether to grant more iterations |

- **Simple encapsulated objects** — NOT a state machine pattern
- Each UseCase handles one well-defined scenario

### FailedToExecutePlanUseCase Detail

When plan execution hits blocking issues (agent calls `/callback-shepherd/fail-workflow`):
1. Spin up a **cleanup agent** (full write access — needs to run cleanup commands)
2. Cleanup agent analyzes the approach taken and why it failed
3. Writes failure summary + learnings into the ticket (so next retry is better informed)
4. Runs cleanup commands to restore codebase to starting state
5. Ticket re-opened via `tk reopen <id>`

### FailedToConvergeUseCase Detail

When the reviewer sends `needs_iteration` but the iteration counter exceeds `iteration.max`:
1. Harness uses **BudgetHigh DirectLLM** to summarize the current state (reviewer's PUBLIC.md + doer's PUBLIC.md + SHARED_CONTEXT.md)
2. Presents summary to user with the iteration history
3. User decides:
   - **Grant more iterations**: user specifies how many additional iterations. `iteration.max` is bumped by that amount. Harness resumes the doer→reviewer loop.
   - **Abort**: triggers `FailedToExecutePlanUseCase` (same cleanup flow)

Note: `iteration.max` is a **budget**, not a hard limit. The user can override it via `FailedToConvergeUseCase`.

---

## Session ID Tracking — AgentSessionIdResolver

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for full details
on HandshakeGuid, AgentSessionIdResolver, and session schema.

---

## DirectLLM — Tier-Scoped Interfaces

For harness-internal tasks (compress ticket title, suggest feature name, summarize convergence failure state).

### Design: Interface-per-Tier

Each budget tier gets its own interface. The `Initializer` wires a concrete `DirectLLM` implementation
to each tier interface — callers depend on the tier interface, never on a specific model.

```kotlin
// Shared contract — all tiers implement this
interface DirectLLM {
    suspend fun call(request: ChatRequest): ChatResponse
}

// Tier interfaces — callers depend on these
interface DirectQuickCheapLLM : DirectLLM    // fast, low-cost tasks (title compression, slugification)
interface DirectMediumLLM : DirectLLM        // mid-tier tasks
interface DirectBudgetHighLLM : DirectLLM    // expensive tasks (convergence failure summarization)
```

### V1 Model Assignments

| Tier Interface | V1 Model | Provider | Typical Use |
|---|---|---|---|
| `DirectQuickCheapLLM` | **GLM-4.7-Flash** | Z.AI (GLM) | Title compression, feature name suggestion |
| `DirectMediumLLM` | TBD | — | Reserved for mid-tier tasks |
| `DirectBudgetHighLLM` | **GLM-5** | Z.AI (GLM) | `FailedToConvergeUseCase` state summarization |

Model assignments are configuration — changing the model behind a tier requires no code changes
outside the `Initializer`.

- **Not used for iteration decisions** — the reviewer's `result` field is authoritative
- Used by `FailedToConvergeUseCase` (via `DirectBudgetHighLLM`) to summarize state for user decision

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

### Sub-Part Transitions

- **Automatic** for doer→reviewer transitions: doer sends `result: "completed"` → reviewer starts
- **Reviewer-driven** for iteration decisions: reviewer sends `result: "pass"` (proceed) or `result: "needs_iteration"` (loop back to doer). The reviewer's verdict is authoritative — no LLM evaluation in this path.

### Context Assembly — ContextForAgentProvider

A [`ContextForAgentProvider`](core/ContextForAgentProvider.md) (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E)
interface is responsible for assembling instruction files for agents:
- Execution agent instructions (role def + ticket + SHARED_CONTEXT.md + prior PUBLIC.md files + callback script usage)
- Planner instructions (ticket + role catalog + plan format instructions)
- PLAN_REVIEWER instructions (includes `plan.json` from `harness_private/`)

### Role Catalog — Auto-Discovered
<!-- ref.ap.iF4zXT5FUcqOzclp5JVHj.E -->

- Every Markdown file in `$TICKET_SHEPHERD_AGENTS_DIR` is an eligible role
- Extract `description` (required) and `description_long` (optional) from YAML frontmatter
- **Fail-fast on startup** if a role referenced in the workflow is missing

### Plan Mutability During Execution

- **Minor adjustments OK**: Implementor can adjust approach within a part
- **Major deviations → fail explicitly**: Agent calls `callback_shepherd.fail-workflow.sh` → `FailedToExecutePlanUseCase`
- Do NOT attempt to patch the plan mid-execution

---

## Git Branch / Feature Naming
<!-- ref.ap.THL21SyZzJhzInG2m4zl2.E -->

Branch is derived from the ticket. Format: `{TICKET_ID}__{slugified_title}__try-{N}`

- `TICKET_ID`: the `id` field from the ticket's YAML frontmatter
- `slugified_title`: the ticket `title` slugified (lowercase, hyphens); compressed via `DirectQuickCheapLLM` if too long
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
| Callback protocol | **Non-blocking HTTP + TMUX delivery** | All callbacks return 200 immediately; responses delivered via TMUX send-keys |
| Iteration decisions | **Reviewer-authoritative** | Reviewer signals `pass`/`needs_iteration` directly; no LLM re-evaluation |
| Callback scripts | **One script per endpoint** | `callback_shepherd.*.sh` — focused, self-documenting, no flag parsing |

---

## Linked Documentation

| Doc | Content |
|-----|---------|
| [`doc/schema/ai-out-directory.md`](schema/ai-out-directory.md) | `.ai_out/` directory tree, scoping rules, cross-agent visibility |
| [`doc/schema/plan-and-current-state.md`](schema/plan-and-current-state.md) | Unified parts/sub-parts schema, iteration semantics, session IDs, plan lifecycle |
| [`doc/core/agent-to-server-communication-protocol.md`](core/agent-to-server-communication-protocol.md) | Full agent↔server protocol — HandshakeGuid, endpoints, payloads, port discovery, user-question flow, callback scripts |
| [`doc/core/ContextForAgentProvider.md`](core/ContextForAgentProvider.md) | Instruction file assembly — content, ordering, visibility rules per agent type |
| [`doc/core/PartExecutor.md`](core/PartExecutor.md) | PartExecutor abstraction — AgentSignal callback bridge, DoerReviewerPartExecutor iteration loop, SubPartInstructionProvider |
| [`doc/core/SessionsState.md`](core/SessionsState.md) | In-memory GUID→session registry, CompletableDeferred callback bridge, concurrency model, relationship to current_state.json |
| [`doc/core/TicketShepherd.md`](core/TicketShepherd.md) | Central coordinator — owns SessionsState, delegates iteration to PartExecutor, orchestrates use cases |
| [`doc/core/TicketShepherdCreator.md`](core/TicketShepherdCreator.md) | Wires all dependencies, creates a ready-to-go TicketShepherd for a single run |
| [`doc/use-case/SpawnTmuxAgentSessionUseCase.md`](use-case/SpawnTmuxAgentSessionUseCase.md) | Agent spawn/resume flow, HandshakeGuid, callback contract, session schema, callback script spec |
| `ai_input/memory/auto_load/1_core_description.md` | Auto-loaded summary for sub-agents — **update if this doc changes** |
