# Shepherd — High-Level Design (V1)

> **This document and its linked `doc/` files are the authoritative specification.**
> When code diverges from these docs, the docs are correct and the code needs updating.
> Tickets and older notes are context — not spec. If a decision is not captured here, it is not decided.

Codename: **TICKET_SHEPHERD**. Package: `com.glassthought.shepherd`.

## Vocabulary

| Term | Definition |
|------|------------|
| **Ticket** | A markdown file with YAML frontmatter (`id`, `title`, `status`). The mandatory starting point for every Shepherd run — defines what needs to be done. Must have `status: in_progress` on entry (see Hard Constraints). Used for branch naming, state tracking, and agent context. |
| **ShepherdServer** (aka Server) | The long-lived HTTP server instance that starts at harness launch and handles all requests from agents. One per harness process. |
| **Agent** | An instance of a code agent (e.g., Claude Code, PI) running in a TMUX session. In the future, multiple agents may be alive simultaneously. |
| **HandshakeGuid** | A harness-generated identifier (`handshake.${UUID}`) assigned to each agent session. Used in all agent↔server communication. See [protocol doc](core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E). |
| **Orchestration Loop** | The harness-side logic that reads the workflow JSON, iterates through parts, and delegates each part to a `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E). The executor owns spawn/wait/iterate for its part. Server callbacks wake executors via `CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E). Not an agent — a Kotlin process. |

---

## Design Principles

### Communication Visibility

All communication between the harness and agents is persisted in `.ai_out/` under each
sub-part's `comm/` directory — `comm/in/instructions.md` (harness→agent) and
`comm/out/PUBLIC.md` (agent→harness). No temporary files outside the repo. This makes the
full input/output loop for every agent interaction inspectable and git-trackable.

### Simplicity in File Structure, History in Git

Each sub-part's `comm/in/instructions.md` and `comm/out/PUBLIC.md` are **overwritten** on
each iteration rather than creating versioned files. The harness commits after each agent
completes, so `instructions.md` (input) and `PUBLIC.md` (output) appear in the **same commit**
— giving a complete picture of each communication round. **Git history is the history of
communication.** This keeps the file structure flat and simple while retaining full visibility
into the iteration dialogue.

Tradeoff: if we later we squash the commits, we will lose the iteration history. However, if we don't do this then we risk over-zealous agent reading older PUBLIC.md files and confusing itself. Hence, this is deemed worthy tradeoff. 

---

## Hard Constraints

- **One TMUX session per sub-part.** A sub-part gets exactly one TMUX session, spawned on first run and kept alive across iteration loops. The session is killed only when the **part** completes. No kill/respawn between iterations.
- **At most 2 sub-parts per part.** First sub-part is the doer (implementor/planner). Optional second sub-part is the reviewer. On review `needs_iteration`, the harness loops back to the doer. This keeps part execution trivially simple.
  - In V2 we may relax this but in V1 this is KISS constraint.
- **Ticket must be `in_progress` on entry.** The ticket passed to `shepherd run` must have `status: in_progress` in its YAML frontmatter. Fail hard if not. Marking the ticket as `in_progress` (and pushing to remote) is the **caller's responsibility** — outside Shepherd's scope.

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
- Manages file-based communication (`comm/in/instructions.md`, `comm/out/PUBLIC.md`, `SHARED_CONTEXT.md`)
- Handles git commits via pluggable `GitCommitStrategy` — see [Git Commit Strategy](core/git.md) (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E)
- Monitors agent health via timeout + ping mechanism (see Agent Health Monitoring)

The harness also runs a **local HTTP server** (Ktor CIO) — starts once, stays alive for
the entire harness process. The server completes `CompletableDeferred<AgentSignal>`
(ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) on `SessionEntry` to wake suspended `PartExecutor` coroutines.

## CLI Entry Point

ap.mmcagXtg6ulznKYYNKlNP.E

**picocli** for CLI parsing. V1 has a single subcommand:

```
shepherd run --workflow <name> --ticket <path> --iteration-max <N>
```

- `--ticket` **(required)**: path to a ticket markdown file. Shepherd always operates on a ticket.
  - Ticket is a markdown file with YAML frontmatter containing at minimum `id`, `title`, and `status` fields.
  - `status` must be `in_progress` — fail hard otherwise (see Hard Constraints).
  - The ticket `id` is used for branch naming and state tracking.
- `--workflow`: workflow definition name (e.g., `straightforward`, `with-planning`)
- `--iteration-max` **(required)**: default iteration budget for reviewer sub-parts. The planner uses this value when generating `plan.json`. For `straightforward` workflows, this overrides `iteration.max` in the static workflow JSON. The user can further override at runtime via `FailedToConvergeUseCase`.

### Startup — Initializer

ap.HRlQHC1bgrTRyRknP3WNX.E

On startup, the CLI delegates to the **`Initializer`** — the true top-level orchestrator
that owns the full startup sequence:

1. **`ContextInitializer`** (ref.ap.9zump9YISPSIcdnxEXZZX.E — defined in code at
   `ContextInitializer.kt`) → builds `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E):
   shared infrastructure (tmux, LLM, logging) that outlives any single ticket.
2. **`ShepherdServer`** startup — Ktor CIO HTTP server on the port specified by `TICKET_SHEPHERD_SERVER_PORT` env var.
3. **`TicketShepherdCreator`** (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — ticket-scoped wiring
   (workflow resolution, branch creation, `SessionsState`, `ContextForAgentProvider`)
   → returns a ready-to-go `TicketShepherd`.
4. **`TicketShepherd.run()`** — drives the workflow.
5. **Cleanup** — `ShepherdContext.close()` for resource teardown.

**Initialization failure cleanup:** If any step fails (e.g., server fails to bind, ticket
parsing fails), the Initializer cleans up in **reverse order** — closing resources from
the latest successful step back to step 1. `ShepherdContext` implements `AsgardCloseable`,
so `close()` handles teardown of all resources it owns.

**Single-instance constraint (V1):** Only one harness instance may run at a time per machine.
On startup, the harness attempts to bind to `TICKET_SHEPHERD_SERVER_PORT`. If the port is
already in use, the harness **fails hard** with a clear error message directing the user to
stop the other instance first.

V1 does not support resume-on-restart — if the harness dies, you start over.
`current_state.json` is written for progress tracking but not consumed on restart.
See [`doc_v2/resume.md`](../doc_v2/resume.md) (ref.ap.LX1GCIjv6LgmM7AJFas20.E) for V2 resume design.

---

## Sub-Agent Invocation — TMUX Only

All agents are spawned as **interactive TMUX sessions** via `TmuxSessionManager` + `TmuxCommunicator`.
- Why TMUX: 1) to resume (CC --print is not resumable), 2) to observe live.

- **Agent-type abstracted via two interfaces** — each agent type (Claude Code, PI, future agents) provides its own implementations. `SpawnTmuxAgentSessionUseCase` dispatches to the correct pair based on `agentType` from the sub-part config:
  - **`AgentStarter`** (ref.ap.RK7bWx3vN8qLfYtJ5dZmQ.E) — builds the shell command to start the agent in TMUX. V1: `ClaudeCodeAgentStarter`.
  - **`AgentSessionIdResolver`** (ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E) — resolves the agent's session ID from external artifacts. V1: `ClaudeCodeAgentSessionIdResolver`.
- Leverages subscription pricing; interface allows swapping agent implementations
- **Strictly serial** execution for V1 (1 harness → 1 active agent at a time; idle sessions kept alive per Hard Constraints)
- **One TMUX session per sub-part** — kept alive across iteration loops (see Hard Constraints). New instructions delivered via `send-keys`.
- Future: parallel sessions on separate git worktrees (branch as identifier)

### Agent Invocation

Agents are spawned via [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md)
(ref.ap.hZdTRho3gQwgIXxoUtTqy.E). The executor communicates results via
`CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) — see
[`PartExecutor`](core/PartExecutor.md) for the callback bridge design.

- Instructions written to `comm/in/instructions.md` in the sub-part's `.ai_out/` directory (preserves formatting, git-tracked)
- V1: no tool restrictions (allow everything)

---

## Agent↔Harness Communication — Bidirectional
<!-- ap.NAVMACFCbnE7L6Geutwyk.E — HarnessServer implementation -->

Communication between agents and the harness is bidirectional through two distinct channels:
**Agent → Harness** via HTTP POST (two callback scripts: `callback_shepherd.signal.sh` for
fire-and-forget signals, `callback_shepherd.query.sh` for synchronous request/response queries),
and **Harness → Agent** via TMUX `send-keys` (the only way to push content to a running agent).
The harness runs a Ktor CIO server (on the port from `TICKET_SHEPHERD_SERVER_PORT` env var) that stays alive for the entire run.

**Two-tier endpoint design**: Signal endpoints (`/callback-shepherd/signal/*`) are fire-and-forget —
every signal script call expects bare 200 and returns immediately. Query endpoints
(`/callback-shepherd/query/*`) return meaningful response bodies for the agent to act on.
When the harness needs to deliver content back to the agent (Q&A answers, iteration instructions),
it uses TMUX `send-keys`. No long-lived HTTP connections.

See [Agent-to-Server Communication Protocol](core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the full protocol specification: endpoints, payloads, HandshakeGuid identity, port discovery, user-question flow, and callback scripts.

### Agent Lifecycle — Spawn

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for:
- New session spawn flow (GUID generation, env var export, session ID resolution)
- Session schema in `current_state.json`
- Callback script spec

---

## Agent Health Monitoring

Timeout + ping mechanism to detect crashed/hung agents. Five UseCase classes handle distinct
failure scenarios (`NoStartupAckUseCase`, `NoStatusCallbackTimeOutUseCase`, `NoReplyToPingUseCase`,
`FailedToExecutePlanUseCase`, `FailedToConvergeUseCase`). Agents call `callback_shepherd.signal.sh started`
immediately after reading instructions — a 3-minute startup timeout catches spawn failures fast
(ref.ap.xVsVi2TgoOJ2eubmoABIC.E). See [Health Monitoring](use-case/HealthMonitoring.md)
(ref.ap.RJWVLgUGjO5zAwupNLhA0.E) for the full spec — flow, triggers, actions, and UseCase
naming principle.

---

## Session ID Tracking — AgentSessionIdResolver

Claude Code does **not** expose its session ID to the agent from within its own context
(validated). The `AgentSessionIdResolver` **interface** (ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E)
abstracts session ID discovery so each agent type can provide its own resolver
(`ClaudeCodeAgentSessionIdResolver` scans JSONL files; future agent types will differ).
Session IDs are recorded in `current_state.json` for **inspection and debugging** (V1)
and **resume** (V2).

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for full details
on HandshakeGuid, AgentSessionIdResolver (interface rationale, integration testing guidance),
and session schema.

---

## DirectLLM — Tier-Scoped Interfaces

Interface-per-tier design (`DirectQuickCheapLLM`, `DirectBudgetHighLLM`). V1: two tiers.
Not used for iteration decisions — the reviewer's verdict is authoritative. See
[DirectLLM](core/DirectLLM.md) (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) for tier assignments and
contract.

---

## Workflow Definition, File Structure, and Iteration Semantics

See:
- [`doc/schema/ai-out-directory.md`](schema/ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) — `.ai_out/` directory tree, scoping rules, cross-agent visibility via ContextForAgentProvider
- [`doc/schema/plan-and-current-state.md`](schema/plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E) — unified parts/sub-parts schema, `plan.json` / `current_state.json` lifecycle, iteration semantics, session ID storage

**Key points:**
- **JSON** under `./config/workflows/`. **Jackson + Kotlin module** for serialization.
- `--workflow <name>` → loads `./config/workflows/<name>.json`. Fail-fast if not found.
- Two modes: **straightforward** (static parts) and **with-planning** (planning loop → dynamic execution).
- `current_state.json` in `harness_private/` is the single source of truth for plan + progress + session IDs.

### Sub-Part Transitions

- **Automatic** for doer→reviewer transitions: doer sends `result: "completed"` → reviewer starts
- **Reviewer-driven** for iteration decisions: reviewer sends `result: "pass"` (proceed) or `result: "needs_iteration"` (loop back to doer). The reviewer's verdict is authoritative — no LLM evaluation in this path. On `needs_iteration`, the reviewer must follow the [Structured Reviewer Feedback Contract](core/ContextForAgentProvider.md#structured-reviewer-feedback-contract--apeslyjmfqq8bbrfxczyw5pe) (ref.ap.EslyJMFQq8BBrFXCzYw5P.E).

### Context Assembly — ContextForAgentProvider

A [`ContextForAgentProvider`](core/ContextForAgentProvider.md) (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E)
interface is responsible for assembling instruction files for agents:
- Execution agent instructions (role def + ticket + SHARED_CONTEXT.md + prior PUBLIC.md files + callback script usage)
- Planner instructions (ticket + role catalog + plan format instructions)
- PLAN_REVIEWER instructions (includes `plan.json` from `harness_private/`)

### Role Catalog — Auto-Discovered
<!-- ap.iF4zXT5FUcqOzclp5JVHj.E -->

- Every Markdown file in `$TICKET_SHEPHERD_AGENTS_DIR` is an eligible role
- Extract `description` (required) and `description_long` (optional) from YAML frontmatter
- **Roles do NOT specify `agentType` or `model`** — those are workflow-level decisions
  assigned by the planner (for `with-planning` workflows) or by the static workflow JSON
  (for `straightforward` workflows). See [Agent Type & Model Assignment](#agent-type--model-assignment).
- **Fail-fast on startup** if a role referenced in the workflow is missing

#### Agent Roles Directory Structure
<!-- ap.Q7kR9vXm3pNwLfYtJ8dZs.E -->

Agent role definitions live in `_config/agents/`:

```
_config/agents/
├── input/              # Editable source files — make role edits here
│   ├── IMPLEMENTATION.md
│   ├── IMPLEMENTATION_REVIEWER.md
│   └── ...
├── generate.sh         # Generates ready-to-use files from input/
└── _generated/         # Generated output — $TICKET_SHEPHERD_AGENTS_DIR points here
    ├── IMPLEMENTATION.md
    ├── IMPLEMENTATION_REVIEWER.md
    └── ...
```

- **`input/`** — the authoritative source for role definitions. All editing happens here.
- **`_generated/`** — output of `generate.sh`. This is what `$TICKET_SHEPHERD_AGENTS_DIR`
  points to at runtime. Do not edit directly.
- **`$TICKET_SHEPHERD_AGENTS_DIR`** (required env var) — must point to the `_generated/`
  directory. Validated at harness initialization (see [Required Environment Variables](core/git.md#required-environment-variables)).

### Agent Type & Model Assignment
<!-- ap.Xt9bKmV2wR7pLfNhJ3cQy.E -->

**Roles define behavior, not infrastructure.** Which agent implementation (`agentType`) and
which model (`model`) to use are **workflow-level decisions**, not role-level properties.

| Workflow type | Who assigns `agentType` + `model`? | Where it lives |
|---|---|---|
| `with-planning` | **Planner agent** — assigns per sub-part in `plan.json` | `plan.json` → `current_state.json` |
| `straightforward` | **Static workflow JSON** — specified per sub-part | `config/workflows/*.json` → `current_state.json` |

This design supports varying agents across sub-parts (e.g., ClaudeCode-opus for planning,
PI-glm-5 for implementation) without touching role definitions. The planner receives
available agent types and model options as part of its instructions
(ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) and makes assignment decisions per sub-part.

**V1 constraint:** Only `ClaudeCode` agent type is supported. Model options within
ClaudeCode: `opus` (high), `sonnet` (budget-high).

**Session records store actual model names** (e.g., `"sonnet"`, `"opus"`, `"glm-5"`), never
tier names like `"BudgetHigh"`. This is critical for V2 resume
(ref.ap.LX1GCIjv6LgmM7AJFas20.E) — by the time we resume, a tier's backing model may
have changed, and we need the exact model to match the session.

### Plan Mutability During Execution

- **Minor adjustments OK**: Implementor can adjust approach within a part
- **Major deviations → fail explicitly**: Agent calls `callback_shepherd.signal.sh fail-workflow` → `FailedToExecutePlanUseCase`
- Do NOT attempt to patch the plan mid-execution

---

## Git — Branch Naming, Commits

Branch naming (format, try-N resolution), commit strategy (timing, message format, author
attribution), and all env var requirements are fully specified in
[`doc/core/git.md`](core/git.md) (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).

**Key points:**
- **Clean working tree required** — `git status --porcelain` must be empty at startup. Fail hard if not. Prevents mixing human WIP with agent output. (ref.ap.QL051Wl21jmmYqTQTLglf.E)
- Branch format: `{TICKET_ID}__{slugified_title}__try-{N}` — owned by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
- Harness owns all git commits — agents never commit. Pluggable `GitCommitStrategy` interface.
- V1 default: `CommitPerSubPart` — commits the entire working tree (`git add -A`)
- **Git operation failures** trigger `GitOperationFailureUseCase` → `AutoRecoveryByAgentUseCase` (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E, ref.ap.q54vAxzZnmWHuumhIQQWt.E) — runs a PI agent (`$AI_MODEL__ZAI__FAST`) via `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) to fix the environment, then retries once. Falls back to `FailedToExecutePlanUseCase` if recovery fails.

## Harness-Level Resume — V2

V1 does **not** support resume-on-restart — if the harness dies, you start over.
`current_state.json` is written for progress tracking but not consumed on restart.
V2 resume design: [`doc_v2/resume.md`](../doc_v2/resume.md) (ref.ap.LX1GCIjv6LgmM7AJFas20.E).

---

## Key Technology Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Workflow format | **JSON** | Easy for LLMs to generate; strong tooling |
| Workflow schema | **Unified parts/sub-parts** | Same schema for static workflows and planner output; one parser |
| JSON library | **Jackson + Kotlin module** | Battle-tested, runtime reflection |
| CLI parser | **picocli** | Mature, annotation-driven |
| HTTP server | **Ktor CIO** | Coroutine-native, Kotlin ecosystem |
| Server port | **Stable via env var** | `TICKET_SHEPHERD_SERVER_PORT` — simple, explicit, no temp files; fail hard if port in use |
| Agent start command | **AgentStarter interface** (ref.ap.RK7bWx3vN8qLfYtJ5dZmQ.E) | Different agent types have different CLI invocations. Interface required: each `AgentType` provides its own `AgentStarter` (OCP). V1: `ClaudeCodeAgentStarter`. |
| Session tracking | **AgentSessionIdResolver interface** | Claude Code cannot expose its session ID to the agent (validated). Interface required: different agent types have different discovery mechanisms (OCP). Session IDs recorded for inspection (V1) + resume (V2). `ClaudeCodeAgentSessionIdResolver` impl scans JSONL files. |
| Session storage | **`sessionIds` array in `current_state.json`** | All state in one file; session history tracked for V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E) |
| Package | **com.glassthought.shepherd** | Shepherd as sub-package under glassthought |
| Q&A mode | **`UserQuestionHandler` strategy** (ref.ap.NE4puAzULta4xlOLh5kfD.E) | V1: `StdinUserQuestionHandler` (human at terminal, stdin/stdout, blocks indefinitely). Strategy interface enables future swap to LLM/Slack/timeout-with-fallback. |
| Role catalog | **Auto-discovered from `$TICKET_SHEPHERD_AGENTS_DIR`** | Every .md file is eligible; `description` from frontmatter; roles define behavior only — no `agentType`/`model` |
| Agent type + model | **Assigned by planner or workflow JSON** (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E) | Planner decides per sub-part (with-planning); static in workflow JSON (straightforward). Session records store actual model names, never tier names. |
| Plan mutability | **Frozen; minor tweaks OK** | Major deviations → fail explicitly (`FailedToExecutePlanUseCase` — red error, halt) |
| Callback protocol | **Two-tier: signals (fire-and-forget) + queries (synchronous response)** | Signal endpoints return bare 200; query endpoints return meaningful response body; harness-to-agent delivery via TMUX send-keys |
| Iteration decisions | **Reviewer-authoritative** | Reviewer signals `pass`/`needs_iteration` directly; no LLM re-evaluation. `needs_iteration` requires structured feedback (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) |
| Durable pitfall docs | **WHY-NOT comments** (ref.ap.kmiKk7vECiNSpJjAXYMyE.E) | Date-stamped inline comments at code locations where wrong approaches are tempting. Three sources: reviewer→doer, doer pushback, doer self-discovered. Not immutable — best understanding at that time. |
| Startup acknowledgment | **`/callback-shepherd/signal/started`** (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Agent calls immediately after reading instructions. 3-min `noStartupAckTimeout` catches spawn failures 10x faster than general 30-min timeout. Side-channel signal — updates `lastActivityTimestamp`, no AgentSignal. |
| Callback scripts | **One script per tier** | `callback_shepherd.signal.sh` (fire-and-forget) + `callback_shepherd.query.sh` (synchronous response) — tier name makes contract obvious |
| Git commits | **Harness-owned, pluggable strategy** | `GitCommitStrategy` interface; V1 default `CommitPerSubPart`; author encodes agent+model+version+user |
| Cross-try learning | **Ticket mutation via NonInteractiveAgentRunner** | On failure, run ClaudeCode `--print` (sonnet) via `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) to read `.ai_out/` artifacts, generate failure summary, and append `## Previous Failed Attempts` section to the ticket. Agent handles git commit + best-effort propagation. Ticket already feeds into agent context — no plumbing changes needed. |
| System prompt | **Always override via `--system-prompt-file`** | Stage-specific prompts: `for_planning.md` (planning) / `default.md` (execution) from `${MY_ENV}/config/claude/ai_input/system_prompt/`. Hard fail if missing. See [SpawnTmuxAgentSessionUseCase — System Prompt File Resolution](use-case/SpawnTmuxAgentSessionUseCase.md#system-prompt-file-resolution). |

---

## Linked Documentation

| Doc | Content |
|-----|---------|
| [`doc/schema/ai-out-directory.md`](schema/ai-out-directory.md) | `.ai_out/` directory tree, scoping rules, cross-agent visibility |
| [`doc/schema/plan-and-current-state.md`](schema/plan-and-current-state.md) | Unified parts/sub-parts schema, plan lifecycle, session ID storage |
| [`doc/core/agent-to-server-communication-protocol.md`](core/agent-to-server-communication-protocol.md) | Agent↔server protocol — HandshakeGuid, endpoints, payloads, port discovery, callback scripts |
| [`doc/core/ContextForAgentProvider.md`](core/ContextForAgentProvider.md) | Instruction file assembly — content, ordering, visibility rules per agent type, structured reviewer feedback contract, WHY-NOT comments protocol |
| [`doc/core/PartExecutor.md`](core/PartExecutor.md) | PartExecutor abstraction — AgentSignal callback bridge, DoerReviewerPartExecutor iteration loop, SubPartInstructionProvider |
| [`doc/core/SessionsState.md`](core/SessionsState.md) | In-memory GUID→session registry, CompletableDeferred callback bridge |
| [`doc/core/TicketShepherd.md`](core/TicketShepherd.md) | Central coordinator — owns SessionsState, delegates iteration to PartExecutor, orchestrates use cases |
| [`doc/core/TicketShepherdCreator.md`](core/TicketShepherdCreator.md) | Wires all dependencies, creates a ready-to-go TicketShepherd for a single run |
| [`doc/core/git.md`](core/git.md) | Git — branch naming, try-N resolution, commit strategy, author attribution, env var requirements |
| [`doc/core/DirectLLM.md`](core/DirectLLM.md) | DirectLLM tier-scoped interfaces, V1 model assignments |
| [`doc/core/UserQuestionHandler.md`](core/UserQuestionHandler.md) | User-question strategy interface, V1 stdin behavior, flow |
| [`doc/use-case/SpawnTmuxAgentSessionUseCase.md`](use-case/SpawnTmuxAgentSessionUseCase.md) | Agent spawn flow, HandshakeGuid, session ID resolution |
| [`doc/use-case/HealthMonitoring.md`](use-case/HealthMonitoring.md) | Health monitoring UseCases — startup ack, timeout, ping, crash, convergence failure |
| [`doc/core/NonInteractiveAgentRunner.md`](core/NonInteractiveAgentRunner.md) | Lightweight subprocess-based agent invocation (`--print` mode) for utility tasks — recovery, failure analysis |
| [`doc/use-case/AutoRecoveryByAgentUseCase.md`](use-case/AutoRecoveryByAgentUseCase.md) | Generic agent-based recovery from infrastructure failures (e.g., git commit failure) |
| [`doc/use-case/TicketFailureLearningUseCase.md`](use-case/TicketFailureLearningUseCase.md) | Records structured failure context + LLM summary into ticket on workflow failure — enables cross-try learning |
| `ai_input/memory/auto_load/1_core_description.md` | Auto-loaded summary for sub-agents — **update if this doc changes** |
