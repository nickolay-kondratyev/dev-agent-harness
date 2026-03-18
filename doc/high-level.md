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

- **One TMUX session per sub-part at a time.** A sub-part gets exactly one TMUX session, spawned on first run and kept alive across iteration loops. The session is killed when the **part** completes, or when **self-compaction** triggers session rotation (ref.ap.8nwz2AHf503xwq8fKuLcl.E). After session rotation, a new session is spawned for the same sub-part. No two sessions are alive simultaneously for the same sub-part.
- **Agents under TMUX are spawned in Interactive mode.** This is the entire point of using TMUX — to allow the harness to send further input to a running agent via `send-keys`. The `-p`/`--print` flags must **NOT** be used for TMUX-spawned agents, as they produce non-interactive (run-and-exit) sessions. The bootstrap message is delivered as an **initial prompt argument** in the CLI command (not via `send-keys`) — the agent receives it atomically on startup. All subsequent communication (instructions, pings, Q&A) uses `send-keys`. Non-interactive (`--print`) mode is reserved exclusively for utility use cases via `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E).
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
- Manages file-based communication (`comm/in/instructions.md`, `comm/out/PUBLIC.md`)
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
- `--iteration-max` **(required)**: default iteration budget for reviewer sub-parts. The planner uses this value when generating `plan_flow.json`. For `straightforward` workflows, this overrides `iteration.max` in the static workflow JSON. The user can extend the budget in fixed increments at runtime via `FailedToConvergeUseCase` (y/N prompt).

### Startup — Initializer

ap.HRlQHC1bgrTRyRknP3WNX.E

On startup, the CLI delegates to the **`Initializer`** — the true top-level orchestrator
that owns the full startup sequence:

0. **`EnvironmentValidator.validate()`** (ref.ap.A8WqG9oplNTpsW7YqoIyX.E) — runs **before**
   any infrastructure is created. **Collects all failures before throwing** — so the user
   sees every missing prerequisite in one run instead of N sequential fix-and-retry cycles.
   Checks:
   - **Docker**: process is running inside a Docker container (`/.dockerenv` must exist).
     Agents are spawned with `--dangerously-skip-permissions` which is only safe inside a
     container.
   - **tmux**: `tmux` binary must be on `$PATH` (`which tmux` succeeds). Required for agent
     session management; without it the harness cannot function.
   - **Required env vars**: all `Constants.REQUIRED_ENV_VARS.ALL` are present and non-blank.

   If any checks fail, throws `IllegalStateException` with a **consolidated** error listing
   every issue at once, e.g.:
   ```
   Environment validation failed:
     - Not running in a Docker container (/.dockerenv not found). Shepherd requires Docker for safety.
     - tmux not found on $PATH. Install: apt install tmux
     - Missing required env vars: Z_AI_API_TOKEN, TICKET_SHEPHERD_SERVER_PORT
   ```
   **Implementation pattern**: each check is a pure function returning `String?` — `null`
   on success, a human-readable error message on failure. `validate()` collects non-null
   results into a `List<String>` and throws once at the end if the list is non-empty.
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
- Why TMUX: 1) to send further input to a running agent (`send-keys`), 2) to resume (CC `--print` is not resumable), 3) to observe live.

- **Agent-type abstracted via `AgentTypeAdapter` interface** (ref.ap.A0L92SUzkG3gE0gX04ZnK.E) — each agent type (Claude Code, PI, future agents) provides a single implementation that encapsulates both the start command builder and session ID resolver. `SpawnTmuxAgentSessionUseCase` dispatches to the matching adapter based on `agentType` from the sub-part config. V1: `ClaudeCodeAdapter`.
- Leverages subscription pricing; interface allows swapping agent implementations
- **Strictly serial** execution for V1 (1 harness → 1 active agent at a time; idle sessions kept alive per Hard Constraints)
- **One TMUX session per sub-part** — kept alive across iteration loops (see Hard Constraints). New instructions delivered via `send-keys`.
- Future: parallel sessions on separate git worktrees (branch as identifier)

### AgentFacade — Testability Facade

The orchestration layer (`PartExecutor`, `TicketShepherd`) accesses **all** agent operations
through a single **`AgentFacade`** interface (ref.ap.9h0KS4EOK5yumssRCJdbq.E). This is
the testability seam between orchestration logic and infrastructure. The real implementation
(`AgentFacadeImpl`) delegates to the existing infra components (`AgentTypeAdapter`
(ref.ap.A0L92SUzkG3gE0gX04ZnK.E), `TmuxSessionManager`, `TmuxCommunicator`,
`ContextWindowStateReader`, `SessionsState`). None of these infra components are visible
to the orchestration layer.

The interface exposes high-level operations: spawn agent (returning a handle with a
`Deferred<AgentSignal>`), send payload with ACK, send health ping, read context window state,
and kill session. `SessionsState` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) is an **internal
implementation detail** of `AgentFacadeImpl` — `PartExecutor` never touches it directly.

See [`AgentFacade`](core/AgentFacade.md) (ref.ap.9h0KS4EOK5yumssRCJdbq.E) for the
full spec — interface shape, decisions, signal delivery ownership, and spec impact.

### Agent Invocation

Agents are spawned via [`AgentFacade.spawnAgent()`](core/AgentFacade.md)
(ref.ap.9h0KS4EOK5yumssRCJdbq.E), which encapsulates the full
[`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md)
(ref.ap.hZdTRho3gQwgIXxoUtTqy.E) flow internally. The returned `SpawnedAgentHandle`
includes a `Deferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) that the executor
awaits — see [`PartExecutor`](core/PartExecutor.md) for the state machine design.

- Instructions written to `comm/in/instructions.md` in the sub-part's `.ai_out/` directory (preserves formatting, git-tracked)
- V1: no tool restrictions (allow everything)

---

## Agent↔Harness Communication — Bidirectional
<!-- ap.NAVMACFCbnE7L6Geutwyk.E — HarnessServer implementation -->

Communication between agents and the harness is bidirectional through two distinct channels:
**Agent → Harness** via HTTP POST (`callback_shepherd.signal.sh` — fire-and-forget signals),
and **Harness → Agent** via TMUX `send-keys` (the only way to push content to a running agent).
The harness runs a Ktor CIO server (on the port from `TICKET_SHEPHERD_SERVER_PORT` env var) that stays alive for the entire run.

**Signal endpoint design**: All agent-to-harness endpoints (`/callback-shepherd/signal/*`) are fire-and-forget —
the signal script call expects bare 200 and returns immediately. Any harness-to-agent content
(Q&A answers, iteration instructions) comes via TMUX `send-keys` wrapped with the
**Payload Delivery ACK Protocol** (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) — the agent must ACK
receipt before processing, ensuring every instruction is confirmed delivered. No long-lived HTTP connections.

See [Agent-to-Server Communication Protocol](core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the full protocol specification: endpoints, payloads, HandshakeGuid identity, port discovery, user-question flow, and callback scripts.

### Agent Lifecycle — Spawn

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for:
- New session spawn flow (GUID generation, env var export, session ID resolution)
- Session schema in `current_state.json`
- Callback script spec

---

## Agent Health Monitoring

Timeout + ping mechanism to detect crashed/hung agents. Liveness is determined **solely by
HTTP callback timestamps** (`SessionEntry.lastActivityTimestamp`
ref.ap.dnc1m7qKXVw2zJP8yFRE.E) — updated on every agent → server callback. When no callback
arrives within `healthTimeouts.normalActivity` (30 min default), the harness
pings the agent; if no reply within `healthTimeouts.pingResponse` (3 min), the agent is declared crashed.

Three UseCase classes handle distinct failure scenarios (`AgentUnresponsiveUseCase`,
`FailedToExecutePlanUseCase`, `FailedToConvergeUseCase`). `AgentUnresponsiveUseCase` is
parameterized by `DetectionContext` (`STARTUP_TIMEOUT`, `NO_ACTIVITY_TIMEOUT`, `PING_TIMEOUT`)
— consolidating the three unresponsive-agent paths into one class with context-specific logging.
Agents call `callback_shepherd.signal.sh started` immediately after
reading instructions — a 3-minute startup timeout catches spawn failures fast
(ref.ap.xVsVi2TgoOJ2eubmoABIC.E). See [Health Monitoring](use-case/HealthMonitoring.md)
(ref.ap.RJWVLgUGjO5zAwupNLhA0.E) for the full spec — flow, triggers, actions, and UseCase
naming principle.

> **Note:** `context_window_slim.json` is used for **done-boundary compaction decisions only**
> (ref.ap.8nwz2AHf503xwq8fKuLcl.E), not for liveness detection. This is a deliberate
> simplification — see [HealthMonitoring.md Simplification Tradeoff](use-case/HealthMonitoring.md#liveness-model-http-callbacks-only--apdnc1m7qkxvw2zjp8yfre).

## Context Window Monitoring & Self-Compaction

Detects context window exhaustion in TMUX-powered agents and performs controlled
self-compaction at **done boundaries** — killing the old session and spawning a fresh one
with a `PRIVATE.md` context summary.

**V1 approach:** Claude Code's built-in auto-compaction remains **enabled** for emergency
mid-task compaction. The harness performs **controlled self-compaction only at done
boundaries** (soft threshold at 35% remaining / 65% used). This eliminates the complex
Ctrl+C emergency interrupt path. See
[`doc_v2/our-own-emergency-compression.md`](../doc_v2/our-own-emergency-compression.md) for
the deferred V2 harness-controlled emergency compression design.

After self-compaction, the agent's TMUX session is killed and a new one spawned. The new
session receives `PRIVATE.md` (the compressed context summary) via `ContextForAgentProvider`
(ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). Context state is read from
`${HOME}/.vintrin_env/claude_code/session/<SessionID>/context_window_slim.json` — an
external hook artifact. File not present → hard stop failure.

See [`ContextWindowSelfCompactionUseCase`](use-case/ContextWindowSelfCompactionUseCase.md)
(ref.ap.8nwz2AHf503xwq8fKuLcl.E) for the full spec — done-boundary trigger, compaction flow,
thresholds, signal protocol, PRIVATE.md schema, and session rotation mechanics.

---

## Testing Strategy — Fake-Driven Unit Coverage

### Architectural Principle

The orchestration layer (`PartExecutor`, `TicketShepherd`) contains the most complex logic in
the system: a state machine with spawn, timeout, health monitoring, iteration loops, crash
detection, and context window exhaustion handling. Testing this with real agents and real TMUX
sessions is slow (~minutes per test), flaky (real infrastructure), expensive (LLM API calls),
and cannot cover edge cases like crash-after-timeout or callback-stale scenarios.

**Solution:** The `AgentFacade` facade (ref.ap.9h0KS4EOK5yumssRCJdbq.E) is a testability
seam. A `FakeAgentFacade` replaces all agent infrastructure in unit tests, giving full
programmatic control over agent behavior — when spawns complete, when signals arrive, what
context window state looks like, whether ACKs succeed.

### Testing Pyramid

| Layer | What it tests | How | Speed |
|-------|--------------|-----|-------|
| **Unit tests (primary coverage)** | Orchestration state machine — PartExecutor happy path, iteration loops, timeout/crash detection, health monitoring decisions, ACK failures, context window exhaustion | `FakeAgentFacade` + virtual time (`TestClock` + `kotlinx-coroutines-test`) | Milliseconds |
| **Integration tests (sanity checks)** | Real infra works — TMUX sessions spawn, send-keys delivers, HTTP callbacks arrive, session ID resolves | Real `AgentFacadeImpl` + real agent (one or few sessions) | Minutes |

**Unit tests are the primary coverage layer.** Every edge case in the health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E), every branch of the PartExecutorImpl iteration flow
(ref.ap.mxIc5IOj6qYI7vgLcpQn5.E), every failure mode — tested with deterministic fakes and
virtual time. No flakiness from real infrastructure.

**Integration tests are sanity checks.** They verify that the real plumbing connects: real TMUX
session → real agent → real HTTP callback → real deferred completion. A handful of integration
tests covering the happy path and one failure scenario. Their purpose is to catch API contract
changes, not to exercise edge cases.

### Virtual Time — Full Control of the Time Axis

The health-aware await loop has two kinds of timing dependencies, both must be controllable:

| Dependency | Mechanism | Test control |
|-----------|-----------|-------------|
| Coroutine delays (`delay()`, `withTimeout()`) | Health check intervals, ACK timeouts, ping timeouts | `kotlinx-coroutines-test` `TestDispatcher` + `advanceTimeBy()` |
| Wall-clock reads (`now()` for timestamp age comparisons) | `lastActivityTimestamp` age | `Clock` interface with `TestClock` (ref.ap.whDS8M5aD2iggmIjDIgV9.E) |

Together these give **full deterministic control** over the time dimension. Tests advance
virtual time, set fake timestamps, and verify that the orchestration layer makes the right
decisions (ping, declare crash, trigger compaction) at the right moments.

### FakeAgentFacade — Programmable Agent Behavior

The `FakeAgentFacade` implements `AgentFacade` with full programmatic control:

- **Spawn behavior** — configure whether spawn succeeds, fails, or delays
- **Signal delivery** — complete the `Deferred<AgentSignal>` at controlled times with any
  variant (Done, FailWorkflow, Crashed, SelfCompacted)
- **ACK behavior** — configure whether payload ACK succeeds, times out, or partially fails
- **Context window state** — return programmable `ContextWindowState` (any remaining percentage)
- **Activity timestamps** — control `lastActivityTimestamp` advancement
- **Interaction verification** — assert what was sent (payloads, pings, kill calls) and in
  what order

Each unit test is a clear statement: "given this agent behavior, expect this orchestration
result." No coordinating 5 separate fakes. One fake, one scenario, one assertion.

### What This Enables

Scenarios that are impractical or impossible to test with real agents become trivial:

- Agent crashes 31 minutes into work → verify ping sent, no reply, session killed
- ACK delivery fails 3 times → verify session crashed
- Context window hits 35% at done boundary → verify soft compaction triggered
- Reviewer sends needs_iteration 5 times at budget max → verify FailedToConverge path

All of these run in milliseconds with deterministic outcomes.

---

## Session ID Tracking — AgentTypeAdapter.resolveSessionId()

Claude Code does **not** expose its session ID to the agent from within its own context
(validated empirically). Session ID discovery is part of the `AgentTypeAdapter` interface
(ref.ap.A0L92SUzkG3gE0gX04ZnK.E) — each agent type provides its own `resolveSessionId()`
implementation (`ClaudeCodeAdapter` scans JSONL files; future agent types will differ).
Session IDs are recorded in `current_state.json` for **inspection and debugging** (V1)
and **resume** (V2).

**Why not have the agent self-report its session ID?** Evaluated and rejected: Claude Code
provides no mechanism for a running agent to discover its own session ID (no API, env var,
or CLI flag). GUID handshake + JSONL scanning is the necessary approach, not an
over-engineering to simplify away. See the full rationale in
[SpawnTmuxAgentSessionUseCase — AgentTypeAdapter: Why Not Self-Reporting](use-case/SpawnTmuxAgentSessionUseCase.md#why-not-agent-self-reporting-rejected--do-not-revisit).

See [`SpawnTmuxAgentSessionUseCase`](use-case/SpawnTmuxAgentSessionUseCase.md) for full details
on HandshakeGuid, AgentTypeAdapter (interface rationale, integration testing guidance),
and session schema.

---

## DirectLLM — Single Interface

Single `DirectLLM` interface. Callers receive the appropriately-configured instance via
constructor injection (DIP preserved). Model selection is a constructor parameter of the
implementation, not a type-level distinction. Not used for iteration decisions — the
reviewer's verdict is authoritative. See
[DirectLLM](core/DirectLLM.md) (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) for model assignments and
contract.

---

## Workflow Definition, File Structure, and Iteration Semantics

See:
- [`doc/schema/ai-out-directory.md`](schema/ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) — `.ai_out/` directory tree, scoping rules, cross-agent visibility via ContextForAgentProvider
- [`doc/schema/plan-and-current-state.md`](schema/plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E) — unified parts/sub-parts schema, `plan_flow.json` / `current_state.json` lifecycle, iteration semantics, session ID storage

**Key points:**
- **JSON** under `./config/workflows/`. **Jackson + Kotlin module** for serialization.
- `--workflow <name>` → loads `./config/workflows/<name>.json`. Fail-fast if not found.
- Two modes: **straightforward** (static parts) and **with-planning** (planning loop → dynamic execution).
- `current_state.json` in `harness_private/` is the single source of truth for plan + progress + session IDs.

### Sub-Part Transitions

- **Automatic** for doer→reviewer transitions: doer sends `result: "completed"` → reviewer starts
- **Reviewer-driven** for iteration decisions: reviewer sends `result: "pass"` (proceed) or `result: "needs_iteration"` (loop back to doer). The reviewer's verdict is authoritative — no LLM evaluation in this path. On `needs_iteration`, the reviewer writes individual feedback files to `__feedback/pending/` with severity filename prefixes (`critical__`, `important__`, `optional__`) (ref.ap.3Hskx3JzhDlixTnvYxclk.E) and follows the [Structured Reviewer Feedback Contract](core/ContextForAgentProvider.md#structured-reviewer-feedback-contract--apeslyjmfqq8bbrfxczyw5pe) (ref.ap.EslyJMFQq8BBrFXCzYw5P.E). The harness then drives an **inner feedback loop** — feeding items to the doer one at a time in severity order (critical → important → optional), with self-compaction checkpoints between items (ref.ap.8nwz2AHf503xwq8fKuLcl.E). The doer writes a `## Resolution: ADDRESSED` or `## Resolution: REJECTED` marker in each feedback file; the **harness** reads the marker and moves the file to `addressed/` or `rejected/`. Rejections trigger a bounded per-item negotiation with the reviewer (at most 1 round of disagreement; reviewer is authority). See [Granular Feedback Loop](plan/granular-feedback-loop.md) (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E). The harness enforces that the reviewer's PUBLIC.md exists and is non-empty (ref.ap.THDW9SHzs1x2JN9YP9OYU.E) but does not validate the markdown structure — the format is guidance, not a harness-enforced schema.

### Context Assembly — ContextForAgentProvider

A [`ContextForAgentProvider`](core/ContextForAgentProvider.md) (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E)
interface is responsible for assembling instruction files for agents:
- Execution agent instructions (role def + ticket + PLAN.md + prior PUBLIC.md files + callback script usage)
- Planner instructions (ticket + role catalog + plan format instructions)
- PLAN_REVIEWER instructions (includes `plan_flow.json` from `harness_private/`)

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
| `with-planning` | **Planner agent** — assigns per sub-part in `plan_flow.json` | `plan_flow.json` → `current_state.json` |
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
| Agent interaction facade | **`AgentFacade` interface** (ref.ap.9h0KS4EOK5yumssRCJdbq.E) | Single facade for all agent operations (spawn, send, ping, read state, kill). Orchestration layer (`PartExecutor`) depends on one interface, not 5+ infra components. Enables `FakeAgentFacade` for comprehensive unit testing with virtual time. `SessionsState` is internal to the real impl. |
| Agent-type adaptation | **`AgentTypeAdapter` interface** (ref.ap.A0L92SUzkG3gE0gX04ZnK.E) | Single interface per agent type: `buildStartCommand()` + `resolveSessionId()`. Always deployed together — merging eliminates mismatched-pair risk and simplifies wiring (1 dep instead of 2). Different agent types have different CLI invocations AND session ID discovery. OCP: new agent types add a new `AgentTypeAdapter` implementation. V1: `ClaudeCodeAdapter`. Agents spawned in **interactive mode** (no `-p`/`--print`); bootstrap delivered as **initial prompt argument**. Session ID: Claude Code cannot self-report (validated empirically); GUID handshake + JSONL scanning is required (see [rejected simplification rationale](use-case/SpawnTmuxAgentSessionUseCase.md#why-not-agent-self-reporting-rejected--do-not-revisit)). Session IDs recorded for inspection (V1) + resume (V2). |
| Session storage | **`sessionIds` array in `current_state.json`** | All state in one file; session history tracked for V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E) |
| Package | **com.glassthought.shepherd** | Shepherd as sub-package under glassthought |
| Q&A mode | **`UserQuestionHandler` strategy** (ref.ap.NE4puAzULta4xlOLh5kfD.E) | V1: `StdinUserQuestionHandler` (human at terminal, stdin/stdout, blocks indefinitely). Strategy interface enables future swap to LLM/Slack/timeout-with-fallback. |
| Role catalog | **Auto-discovered from `$TICKET_SHEPHERD_AGENTS_DIR`** | Every .md file is eligible; `description` from frontmatter; roles define behavior only — no `agentType`/`model` |
| Agent type + model | **Assigned by planner or workflow JSON** (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E) | Planner decides per sub-part (with-planning); static in workflow JSON (straightforward). Session records store actual model names, never tier names. |
| Plan mutability | **Frozen; minor tweaks OK** | Major deviations → fail explicitly (`FailedToExecutePlanUseCase` — red error, kills all sessions, exits non-zero) |
| Callback protocol | **Fire-and-forget signals only** | Signal endpoints return bare 200; harness-to-agent delivery via TMUX send-keys |
| Payload delivery ACK | **ACK-before-proceed wrapper on all `send-keys` payloads** (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) | Every `send-keys` payload (including health pings) wrapped in XML with PayloadId (`{handshakeGuid_short_8chars}-{sequenceN}`, e.g., `a1b2c3d4-3`). Agent must `ack-payload` before processing. 3 min ACK timeout, 2 retries. Prevents "alive but never got instruction" loop that health monitoring alone cannot break. |
| Iteration decisions | **Reviewer-authoritative** | Reviewer signals `pass`/`needs_iteration` directly; no LLM re-evaluation. `needs_iteration`: harness enforces PUBLIC.md exists + non-empty (ref.ap.THDW9SHzs1x2JN9YP9OYU.E); structured format (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) is instruction guidance, not harness-validated |
| Pitfall documentation | **Code comments** | Agents document non-obvious decisions and pitfalls in code comments. No structured protocol — just "write good comments." |
| Startup acknowledgment | **`/callback-shepherd/signal/started`** (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Bootstrap message delivered as initial prompt argument when agent starts. Agent calls `callback_shepherd.signal.sh started` as first action. `healthTimeouts.startup` (3 min) catches spawn failures 10x faster than `normalActivity` (30 min). Side-channel signal — updates `lastActivityTimestamp`, no AgentSignal. |
| Callback scripts | **`callback_shepherd.signal.sh`** | Single script for all agent-to-harness communication (fire-and-forget signals) |
| Git commits | **Harness-owned, pluggable strategy** | `GitCommitStrategy` interface; V1 default `CommitPerSubPart`; author encodes agent+model+user (e.g., `CC_sonnet_WITH-username`) |
| Cross-try learning | **Ticket mutation via NonInteractiveAgentRunner** | On failure, run ClaudeCode `--print` (sonnet) via `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) to read `.ai_out/` artifacts, generate failure summary, and append `## Previous Failed Attempts` section to the ticket. Agent handles git commit + best-effort propagation. Ticket already feeds into agent context — no plumbing changes needed. |
| System prompt | **Always override via `--system-prompt-file`** | Stage-specific prompts: `for_planning.md` (planning) / `default.md` (execution) from `${MY_ENV}/config/claude/ai_input/system_prompt/`. Hard fail if missing. See [SpawnTmuxAgentSessionUseCase — System Prompt File Resolution](use-case/SpawnTmuxAgentSessionUseCase.md#system-prompt-file-resolution). |
| Context window monitoring | **ContextWindowStateReader interface** (ref.ap.ufavF1Ztk6vm74dLAgANY.E) | Per-agent-type interface. V1: `ClaudeCodeContextWindowStateReader` reads `context_window_slim.json` (format: `remaining_percentage`). File not present → hard stop. Used for **done-boundary compaction decisions only** (ref.ap.8nwz2AHf503xwq8fKuLcl.E), NOT for liveness (ref.ap.dnc1m7qKXVw2zJP8yFRE.E). OCP: future agent types provide their own reader. |
| Auto-compaction | **Claude Code native auto-compaction enabled; harness adds done-boundary self-compaction** (ref.ap.8nwz2AHf503xwq8fKuLcl.E) | Claude Code's built-in auto-compaction remains enabled for emergency mid-task compaction. The harness adds controlled self-compaction at done boundaries (35% remaining / 65% used) with guided summarization into `PRIVATE.md` + session rotation. V2 will add harness-controlled emergency interrupt — see [`doc_v2/our-own-emergency-compression.md`](../doc_v2/our-own-emergency-compression.md). |
| Self-compaction signal | **`/callback-shepherd/signal/self-compacted`** (ref.ap.HU6KB4uRDmOObD54gdjYs.E) | Lifecycle signal for done-boundary self-compaction. Agent calls after writing PRIVATE.md. Completes signalDeferred with `AgentSignal.SelfCompacted`. |
| Reviewer feedback delivery | **Granular per-item feedback loop** (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) | Reviewer writes individual feedback files to `__feedback/pending/` with severity filename prefix (`critical__`, `important__`, `optional__`) (ref.ap.3Hskx3JzhDlixTnvYxclk.E). Harness feeds items to doer one at a time — critical → important → optional. Doer writes `## Resolution: ADDRESSED/REJECTED` marker; **harness** moves files. Rejections trigger bounded per-item negotiation (1 round — reviewer judges once, final). Self-compaction checkpoints between items. `iteration.current` unchanged by inner loop. |
| Sub-part role tracking | **`SubPartRole` enum (explicit, not derived from position)** | In V1, position is deterministic (0 = DOER, 1 = REVIEWER). The enum is kept explicitly: (1) self-documentation — role is a named concept, not a positional convention, making the `done`-result validation table in the protocol (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) immediately readable; (2) evolvability — future roles (e.g., `FIXER`) are additive enum variants. Not built for those cases now, but the door is propped open. See `SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) for full rationale. |

---

## Linked Documentation

| Doc | Content |
|-----|---------|
| [`doc/schema/ai-out-directory.md`](schema/ai-out-directory.md) | `.ai_out/` directory tree, scoping rules, cross-agent visibility |
| [`doc/schema/plan-and-current-state.md`](schema/plan-and-current-state.md) | Unified parts/sub-parts schema, plan lifecycle, session ID storage |
| [`doc/core/agent-to-server-communication-protocol.md`](core/agent-to-server-communication-protocol.md) | Agent↔server protocol — HandshakeGuid, endpoints, payloads, port discovery, callback scripts |
| [`doc/core/ContextForAgentProvider.md`](core/ContextForAgentProvider.md) | Instruction file assembly — content, ordering, visibility rules per agent type, structured reviewer feedback contract |
| [`doc/core/AgentFacade.md`](core/AgentFacade.md) | AgentFacade facade — testability seam, signal ownership, FakeAgentFacade, virtual time strategy |
| [`doc/core/PartExecutor.md`](core/PartExecutor.md) | PartExecutor abstraction — AgentSignal callback bridge, PartExecutorImpl iteration loop (optional reviewer), calls ContextForAgentProvider directly |
| [`doc/core/SessionsState.md`](core/SessionsState.md) | In-memory GUID→session registry, CompletableDeferred callback bridge (internal to `AgentFacadeImpl`) |
| [`doc/core/TicketShepherd.md`](core/TicketShepherd.md) | Central coordinator — delegates to AgentFacade, delegates iteration to PartExecutor, orchestrates use cases |
| [`doc/core/TicketShepherdCreator.md`](core/TicketShepherdCreator.md) | Wires all dependencies, creates a ready-to-go TicketShepherd for a single run |
| [`doc/core/git.md`](core/git.md) | Git — branch naming, try-N resolution, commit strategy, author attribution, env var requirements |
| [`doc/core/DirectLLM.md`](core/DirectLLM.md) | DirectLLM single interface, V1 model assignments |
| [`doc/core/UserQuestionHandler.md`](core/UserQuestionHandler.md) | User-question strategy interface, V1 stdin behavior, flow |
| [`doc/use-case/SpawnTmuxAgentSessionUseCase.md`](use-case/SpawnTmuxAgentSessionUseCase.md) | Agent spawn flow, HandshakeGuid, session ID resolution |
| [`doc/use-case/HealthMonitoring.md`](use-case/HealthMonitoring.md) | Health monitoring UseCases — startup ack, timeout, ping, crash, convergence failure |
| [`doc/core/NonInteractiveAgentRunner.md`](core/NonInteractiveAgentRunner.md) | Lightweight subprocess-based agent invocation (`--print` mode) for utility tasks — recovery, failure analysis |
| [`doc/use-case/AutoRecoveryByAgentUseCase.md`](use-case/AutoRecoveryByAgentUseCase.md) | Generic agent-based recovery from infrastructure failures (e.g., git commit failure) |
| [`doc/use-case/TicketFailureLearningUseCase.md`](use-case/TicketFailureLearningUseCase.md) | Records structured failure context + LLM summary into ticket on workflow failure — enables cross-try learning |
| [`doc/use-case/ContextWindowSelfCompactionUseCase.md`](use-case/ContextWindowSelfCompactionUseCase.md) | Context window exhaustion detection, done-boundary self-compaction flow, PRIVATE.md, session rotation |
| [`doc/plan/granular-feedback-loop.md`](plan/granular-feedback-loop.md) | Granular per-item feedback loop — `__feedback/` directory (3 dirs: pending/addressed/rejected), harness-owned file movement, resolution markers, per-item rejection negotiation, severity-based processing, part completion guard |
| `ai_input/memory/auto_load/1_core_description.md` | Auto-loaded summary for sub-agents — **update if this doc changes** |
