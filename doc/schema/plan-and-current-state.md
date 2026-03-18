# Plan & Current-State JSON Schema / ap.56azZbk7lAMll0D4Ot2G0.E

For directory layout and file locations see [`.ai_out/` directory schema](ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

---

## In-Memory CurrentState — Single Source of Truth / ap.K3vNzHqR8wYm5pJdL2fXa.E

The **in-memory `CurrentState` object** is the authoritative representation of workflow state
(parts, sub-parts, statuses, iteration counters, and session records). All reads and mutations
go through this in-memory object. `current_state.json` on disk is a **durable copy** — flushed
after every mutation for progress tracking and V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E).

**Key invariant:** No component reads `current_state.json` from disk during a run. All state
queries (sub-part status, session records, iteration counters) are served from the in-memory
`CurrentState`. The disk file exists for:
1. **Durability** — crash recovery in V2
2. **Observability** — human inspection of progress during a run
3. **Post-mortem** — examining final state after completion or failure

**Eliminates dual-state sync:** Previously, `SessionsState` (live handles) and
`current_state.json` (session records) were independent — both had to be updated on spawn
and kept in sync. Now, session records live in the in-memory `CurrentState`, and the disk
file is a passive copy. `SessionsState` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) holds only
**live runtime handles** (TMUX sessions, CompletableDeferreds, lastActivityTimestamp) —
concerns that are inherently transient and never persisted.

---

## Unified Plan Schema — Parts and Sub-Parts

All workflow JSON files (static workflows **and** planner-generated `plan_flow.json`) use the **same**
parts/sub-parts schema. One parser handles everything.

### Part Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | yes | Part identifier (e.g., `"planning"`, `"ui_design"`, `"backend_impl"`). |
| `phase` | yes | Either `"planning"` or `"execution"`. Distinguishes the planning part from execution parts. At most one `"planning"` part exists and it is always the first element. |
| `description` | yes | Human-readable description of what this part does. |
| `subParts` | yes | Array of sub-part definitions. See [SubPart Fields](#subpart-fields) below. |

### SubPart Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | yes | Directory name and identifier (e.g., `"impl"`, `"review"`). Execution order is determined by array position. |
| `role` | yes | Role from the role catalog (`$TICKET_SHEPHERD_AGENTS_DIR/*.md`). |
| `agentType` | yes | Agent implementation to use (e.g., `"ClaudeCode"`, `"PI"`). Assigned by the planner (with-planning workflows) or specified in static workflow JSON (straightforward workflows). Never from role definitions — see ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E. |
| `model` | yes | Actual model name (e.g., `"sonnet"`, `"opus"`, `"glm-5"`). Same assignment source as `agentType`. Must be the **actual model name**, never a tier name like `"BudgetHigh"` — required for V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E). |
| `status` | runtime | Sub-part execution status. Added by harness in the in-memory `CurrentState`. See [SubPartStatus](#subpartstatus). |
| `iteration` | no | Present only on the reviewer sub-part (second sub-part). Contains `max` and runtime `current`. |
| `iteration.max` | yes (when `iteration` present) | Maximum number of times the reviewer can loop back to the doer. This is a **budget** — user can extend in fixed increments (+2) at runtime via `FailedToConvergeUseCase` (y/N prompt). |
| `iteration.current` | runtime | Current iteration count. Added by harness. Starts at `0`, incremented each time the reviewer signals `needs_iteration`. See [Iteration Counter](#iteration-counter). |
| `sessionIds` | no | Array of session records (runtime, added by harness). Last element = current/resumable session. See [Session Record Schema](#session-record-schema--apmwzgc1hykvwu3ijqbtew4e). |

### SubPartStatus

```kotlin
enum class SubPartStatus {
    NOT_STARTED,   // initial state — sub-part has not been attempted
    IN_PROGRESS,   // agent has been spawned and is working
    COMPLETED,     // part completed successfully — doer-only done, or reviewer "pass" (marks both reviewer and doer COMPLETED simultaneously)
    FAILED,        // unrecoverable failure (fail-workflow, crash, failed-to-converge)
}
```

**State transitions:**

```
NOT_STARTED → IN_PROGRESS    (harness spawns agent for this sub-part)
IN_PROGRESS → COMPLETED      (doer-only "completed"; or reviewer "pass" — marks both reviewer and doer COMPLETED)
IN_PROGRESS → FAILED         (fail-workflow, agent crash, or failed-to-converge)
IN_PROGRESS → IN_PROGRESS    (reviewer: "needs_iteration" — counter increments, status stays; doer stays IN_PROGRESS throughout all iterations)
```

State machine is strictly forward-only — no back-transitions. In doer+reviewer parts, the doer's
per-round completion is an internal event that does NOT change SubPartStatus. The doer stays
`IN_PROGRESS` until the entire part completes (reviewer PASS), at which point the executor marks
both the reviewer and the doer `COMPLETED` simultaneously.

**Part-level status is derived** — no explicit part status field:
- All sub-parts `COMPLETED` → part complete
- Any sub-part `FAILED` → part failed
- First non-`COMPLETED` sub-part → resume point

### SubPartStateTransition / ap.EHY557yZ39aJ0lV00gPGF.E

Sealed class encoding every legal SubPartStatus transition. The KDoc on each entry **is** the
state machine diagram — one authoritative place to audit the state machine.

`PartExecutorImpl` calls the validator before every status mutation: no status field update
without a validated transition. Invalid (status, trigger) combinations throw immediately at the
transition site — silent acceptance of invalid transitions is impossible.

```kotlin
sealed class SubPartStateTransition {

    /**
     * NOT_STARTED → IN_PROGRESS
     * Trigger: harness spawns the agent for this sub-part (no AgentSignal involved).
     * Validated by: SubPartStatus.validateCanSpawn() — throws if status != NOT_STARTED.
     */
    object Spawn : SubPartStateTransition()

    /**
     * IN_PROGRESS → COMPLETED
     * Triggers:
     *   - doer (doer-only part):    AgentSignal.Done(DoneResult.COMPLETED)
     *   - reviewer:                 AgentSignal.Done(DoneResult.PASS)
     *   - doer (doer+reviewer part): applied by executor simultaneously with reviewer PASS —
     *     the doer does NOT signal COMPLETED per iteration; it stays IN_PROGRESS until the
     *     part completes, then the executor marks both reviewer and doer COMPLETED.
     */
    object Complete : SubPartStateTransition()

    /**
     * IN_PROGRESS → FAILED
     * Triggers:
     *   - AgentSignal.FailWorkflow(reason)
     *   - AgentSignal.Crashed(details)
     */
    object Fail : SubPartStateTransition()

    /**
     * IN_PROGRESS → IN_PROGRESS  (reviewer sub-part only; status value unchanged)
     * Trigger: reviewer AgentSignal.Done(DoneResult.NEEDS_ITERATION).
     * Side-effect handled by executor (not the validator): iteration.current is incremented.
     */
    object IterateContinue : SubPartStateTransition()

}
```

#### Validator Functions

Two validator functions cover all four transitions. All throw `IllegalStateException` on an
invalid (status, trigger) combination — crash fast, no silent fallback.

```kotlin
/**
 * Maps an AgentSignal to the corresponding SubPartStateTransition for this status.
 * Covers transitions: Complete, Fail, IterateContinue.
 *
 * @throws IllegalStateException if the (status, signal) pair is not a valid transition.
 * Note: AgentSignal.SelfCompacted does NOT trigger a SubPart status change — it is handled
 *       inside the facade's health-aware await loop and is invisible to PartExecutorImpl.
 * Note: In doer+reviewer parts, the executor does NOT call this for the doer's Done(COMPLETED)
 *       signal — the doer stays IN_PROGRESS; the executor just proceeds to instruct the reviewer.
 *       The doer's COMPLETED transition happens separately when reviewer sends PASS.
 */
fun SubPartStatus.transitionTo(signal: AgentSignal): SubPartStateTransition {
    return when (this) {
        IN_PROGRESS -> when (signal) {
            is AgentSignal.Done -> when (signal.result) {
                DoneResult.COMPLETED, DoneResult.PASS -> SubPartStateTransition.Complete
                DoneResult.NEEDS_ITERATION            -> SubPartStateTransition.IterateContinue
            }
            is AgentSignal.FailWorkflow -> SubPartStateTransition.Fail
            is AgentSignal.Crashed      -> SubPartStateTransition.Fail
            AgentSignal.SelfCompacted   ->
                error("SelfCompacted is transparent to SubPart status; handle inside facade, not executor")
        }
        NOT_STARTED ->
            error("Cannot apply AgentSignal to NOT_STARTED; call validateCanSpawn() before spawning")
        COMPLETED ->
            error("COMPLETED is terminal — no further transitions allowed")
        FAILED ->
            error("FAILED is terminal — no further transitions allowed")
    }
}

/**
 * Validates that this sub-part is NOT_STARTED before the harness spawns an agent.
 * Returns SubPartStateTransition.Spawn on success.
 * @throws IllegalStateException if status != NOT_STARTED.
 */
fun SubPartStatus.validateCanSpawn(): SubPartStateTransition.Spawn {
    check(this == NOT_STARTED) { "spawn requires NOT_STARTED, got $this" }
    return SubPartStateTransition.Spawn
}

```

#### Adding New Transitions

Adding a new state or trigger is purely additive:
1. Add the new `SubPartStatus` value (if needed) — compiler enforces exhaustive `when`.
2. Add the new `SubPartStateTransition` entry with KDoc.
3. Handle the new entry in `transitionTo` (or add a new validator for non-signal triggers).
4. Update `PartExecutorImpl` to react to the new transition.

No hunting for scattered validation sites — the sealed `when` branch is the sole site.

### Iteration Counter

The `iteration.current` counter lives on the **reviewer sub-part** alongside `iteration.max`.
It tracks how many times the reviewer has looped back to the doer.

| Value | Meaning |
|-------|---------|
| `0` | Doer's first pass (reviewer has not yet run) |
| `1` | Reviewer ran once and signaled `needs_iteration`; doer is on second pass |
| `N` | Reviewer has signaled `needs_iteration` N times |

**Incremented when:** reviewer signals `needs_iteration` (before resuming the doer).
**Checked against:** `iteration.max` — if `current >= max`, triggers `FailedToConvergeUseCase`.
**Not incremented by:** the inner feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E). Processing
individual feedback items within one iteration does NOT increment the counter — the counter
tracks full reviewer→doer(all items)→reviewer cycles, not individual feedback items.

### plan_flow.json vs PLAN.md — Two Distinct Planner Outputs

The PLANNER agent produces **two separate files** with entirely different purposes:

| File | Consumer | Purpose |
|------|----------|---------|
| `harness_private/plan_flow.json` | Harness (machine-parsed) | Strict workflow definition: which agent roles, models, order, and iteration budgets. The harness validates and merges it into the in-memory `CurrentState` (flushed to `current_state.json`). |
| `shared/plan/PLAN.md` | Execution agents — doers and reviewers (LLM-read) | Human-readable guide: clarified requirements, tradeoffs, architecture constraints, affected file paths, design decisions. Fed to all execution sub-parts (doers and reviewers) in `with-planning` workflows so every agent knows what was planned. |

**Why both — different responsibilities, different consumers:** `plan_flow.json` tells the
**harness** *how to orchestrate the workflow* (which agents, what models, in what order, iteration
budgets). `PLAN.md` tells **execution agents** (both doers and reviewers) *what to build and how* —
so doers implement confidently and reviewers evaluate against the planned approach. They carry
non-overlapping information for non-overlapping consumers — no risk of divergence.

### plan_flow.json / CurrentState Schema

The plan and execution state share the **same** base structure (parts → sub-parts). They differ
in whether runtime fields are present:

| Source | Runtime fields | Authoritative location |
|------|---------------|--------|
| `plan_flow.json` | **Absent** — no `status`, no `iteration.current`, no `sessionIds` | Planner output (disk) |
| In-memory `CurrentState` | **Present** — `status` on every sub-part, `iteration.current` on reviewers, `sessionIds` as sessions are created | In-memory (ref.ap.K3vNzHqR8wYm5pJdL2fXa.E); flushed to `current_state.json` on disk after every mutation |

For straightforward workflows, the harness initializes the in-memory `CurrentState` directly
from the static workflow JSON (no `plan_flow.json` involved) and flushes to disk.

### Planning Part in the Parts Array

For `with-planning` workflows, the planning phase is a **regular entry in the `parts` array**
— the first element, with `"phase": "planning"`. It uses the **exact same schema** as
execution parts — same sub-part fields, same `SubPartStatus` transitions, same `iteration`
counter, same `sessionIds` array.

Every part has a `phase` field:
- `"planning"` — the planning part (at most one, always first in the array)
- `"execution"` — execution parts (populated from `plan_flow.json` after planning converges)

The planning part is special in that it runs **before a plan exists** — it is the part that
*creates* the plan. Its sub-parts (PLANNER, PLAN_REVIEWER) are populated from the workflow
JSON's `planningParts` at workflow init (before any agent runs), not from `plan_flow.json`
conversion. After planning completes, the planning part stays in the `parts` array as a
historical record — it is not removed.

This means:
- Planning sub-part status transitions (`NOT_STARTED` → `IN_PROGRESS` → `COMPLETED`) are
  mutated in the in-memory `CurrentState` and flushed to disk after every transition
- Planning iteration counters (PLAN_REVIEWER's `iteration.current`) are tracked in-memory
- Planning session IDs are stored in the same `sessionIds` array format within `CurrentState`
- V2 resume can recover mid-planning state from `current_state.json` (the durable disk copy)
- **Single code path** for reading/writing all parts — no separate top-level field for
  planning vs the `parts` array

**Example `plan_flow.json` (planner output — no runtime fields, execution parts only):**

```json
{
  "parts": [
    {
      "name": "ui_design",
      "phase": "execution",
      "description": "Design the dashboard UI",
      "subParts": [
        { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet" },
        { "name": "review", "role": "UI_REVIEWER", "agentType": "ClaudeCode", "model": "sonnet",
          "iteration": { "max": 3 } }
      ]
    },
    {
      "name": "backend_impl",
      "phase": "execution",
      "description": "Implement API endpoints",
      "subParts": [
        { "name": "impl", "role": "IMPLEMENTOR", "agentType": "ClaudeCode", "model": "opus" },
        { "name": "review", "role": "IMPLEMENTATION_REVIEWER", "agentType": "ClaudeCode", "model": "sonnet",
          "iteration": { "max": 4 } }
      ]
    }
  ]
}
```

**Example `current_state.json` (mid-execution — runtime fields present):**

```json
{
  "parts": [
    {
      "name": "ui_design",
      "phase": "execution",
      "description": "Design the dashboard UI",
      "subParts": [
        {
          "name": "impl",
          "role": "UI_DESIGNER",
          "agentType": "ClaudeCode",
          "model": "sonnet",
          "status": "IN_PROGRESS",
          "sessionIds": [
            {
              "handshakeGuid": "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "agentSession": { "id": "77d5b7ea-cf04-453b-8867-162404763e18" },
              "agentType": "ClaudeCode",
              "model": "sonnet",
              "timestamp": "2026-03-10T15:30:00Z"
            }
          ]
        },
        {
          "name": "review",
          "role": "UI_REVIEWER",
          "agentType": "ClaudeCode",
          "model": "sonnet",
          "status": "IN_PROGRESS",
          "iteration": { "max": 3, "current": 1 },
          "sessionIds": [
            {
              "handshakeGuid": "handshake.b2c3d4e5-f6a7-8901-bcde-f12345678901",
              "agentSession": { "id": "88e6c8fb-df15-564c-9978-273515874f29" },
              "agentType": "ClaudeCode",
              "model": "sonnet",
              "timestamp": "2026-03-10T15:45:00Z"
            }
          ]
        }
      ]
    },
    {
      "name": "backend_impl",
      "phase": "execution",
      "description": "Implement API endpoints",
      "subParts": [
        {
          "name": "impl",
          "role": "IMPLEMENTOR",
          "agentType": "ClaudeCode",
          "model": "opus",
          "status": "NOT_STARTED"
        },
        {
          "name": "review",
          "role": "IMPLEMENTATION_REVIEWER",
          "agentType": "ClaudeCode",
          "model": "sonnet",
          "status": "NOT_STARTED",
          "iteration": { "max": 4, "current": 0 }
        }
      ]
    }
  ]
}
```

In this example: `ui_design` doer is working on its second pass (reviewer signaled
`needs_iteration` once — `current: 1`). Both doer and reviewer are `IN_PROGRESS` — the doer
is not marked `COMPLETED` until the entire part completes (reviewer PASS). `backend_impl`
hasn't started yet.

**Example `current_state.json` (mid-planning — planning part is first in the parts array):**

```json
{
  "parts": [
    {
      "name": "planning",
      "phase": "planning",
      "description": "Plan the workflow",
      "subParts": [
        {
          "name": "plan",
          "role": "PLANNER",
          "agentType": "ClaudeCode",
          "model": "opus",
          "status": "IN_PROGRESS",
          "sessionIds": [
            {
              "handshakeGuid": "handshake.c3d4e5f6-a7b8-9012-cdef-123456789012",
              "agentSession": { "id": "99f7d9gc-eg26-675d-aa89-384626985g30" },
              "agentType": "ClaudeCode",
              "model": "opus",
              "timestamp": "2026-03-10T14:00:00Z"
            }
          ]
        },
        {
          "name": "plan_review",
          "role": "PLAN_REVIEWER",
          "agentType": "ClaudeCode",
          "model": "opus",
          "status": "IN_PROGRESS",
          "iteration": { "max": 3, "current": 1 },
          "sessionIds": [
            {
              "handshakeGuid": "handshake.d4e5f6a7-b8c9-0123-defa-234567890123",
              "agentSession": { "id": "aag8eahd-fh37-786e-bb90-495737a96h41" },
              "agentType": "ClaudeCode",
              "model": "opus",
              "timestamp": "2026-03-10T14:30:00Z"
            }
          ]
        }
      ]
    }
  ]
}
```

In this example: the planner is working on its second pass (plan reviewer signaled
`needs_iteration` once — `current: 1`). Both planner and plan reviewer are `IN_PROGRESS` —
the planner is not marked `COMPLETED` until the planning part converges (plan reviewer PASS).
No execution parts exist yet in the array — they will be appended from `plan_flow.json` after
planning converges.

### plan_flow.json → CurrentState Lifecycle

1. **With-planning**:
   a. At workflow init, `TicketShepherdCreator` creates the in-memory `CurrentState` with a
      `parts` array containing **one entry**: the planning part (`phase: "planning"`), populated
      from the workflow JSON's `planningParts` (runtime fields added: `status: NOT_STARTED`,
      `iteration.current: 0`). No execution parts yet. Flushed to `current_state.json`.
   b. During planning, the `PartExecutor` updates the planning part's sub-part statuses,
      iteration counters, and session IDs in the in-memory `CurrentState` — identical
      mutation + flush path as execution parts.
   c. Planner writes `plan_flow.json` to `harness_private/` (and `PLAN.md` to `shared/plan/`).
      PLAN_REVIEWER sees `plan_flow.json` via
      `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) (not because it's in `shared/`).
   d. After planning converges, harness converts `plan_flow.json` → **appends** execution
      parts (`phase: "execution"`) to the in-memory `CurrentState`'s `parts` array, flushes
      to `current_state.json`, and deletes `plan_flow.json`. The planning part remains at
      index 0 as a historical record.
2. **Straightforward**: No `plan_flow.json`, no planning part. Harness initializes the
   in-memory `CurrentState` with `parts` (all `phase: "execution"`) directly from
   `config/workflows/straightforward.json` and flushes to disk.

**Conversion adds these runtime fields to execution parts:**
- `status: "NOT_STARTED"` on every sub-part
- `iteration.current: 0` on every reviewer sub-part (where `iteration` block exists)
- `sessionIds` is absent initially — added to in-memory `CurrentState` when agents are spawned

### Workflow JSON Schema (static workflows)

Static workflow definitions (`config/workflows/*.json`) use the **same** sub-parts structure.

**`config/workflows/straightforward.json`:**

```json
{
  "name": "straightforward",
  "parts": [
    {
      "name": "main",
      "phase": "execution",
      "description": "Implement and review",
      "subParts": [
        { "name": "impl", "role": "IMPLEMENTATION_WITH_SELF_PLAN", "agentType": "ClaudeCode", "model": "sonnet" },
        { "name": "review", "role": "IMPLEMENTATION_REVIEWER", "agentType": "ClaudeCode", "model": "sonnet",
          "iteration": { "max": 4 } }
      ]
    }
  ]
}
```

**`config/workflows/with-planning.json`:**

```json
{
  "name": "with-planning",
  "planningParts": [
    {
      "name": "planning",
      "phase": "planning",
      "description": "Plan the workflow",
      "subParts": [
        { "name": "plan", "role": "PLANNER", "agentType": "ClaudeCode", "model": "opus" },
        { "name": "plan_review", "role": "PLAN_REVIEWER", "agentType": "ClaudeCode", "model": "opus",
          "iteration": { "max": 3 } }
      ]
    }
  ],
  "executionPhasesFrom": "plan_flow.json"
}
```

Note: `agentType` and `model` on `planningParts` sub-parts are specified in the static workflow
JSON — the planner cannot assign its own agent type. The planner assigns `agentType` and `model`
only for the **execution parts** it generates in `plan_flow.json`
(ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E).

### WorkflowDefinition — Two Modes

A workflow is either **straightforward** (has `parts`) or **with-planning** (has `planningParts` +
`executionPhasesFrom`). These are mutually exclusive — exactly one set of fields will be non-null.

- **Straightforward**: `parts` contains the full execution plan (static, all `phase: "execution"`).
- **With-planning**: `planningParts` defines the planning loop (PLANNER ↔ PLAN_REVIEWER) using
  the same part/sub-parts schema with `phase: "planning"`. `executionPhasesFrom` names the file
  the planner generates (e.g., `"plan_flow.json"`) in `harness_private/`. After planning
  converges, the harness appends execution parts to the in-memory `CurrentState`'s `parts`
  array, flushes to `current_state.json`, and deletes `plan_flow.json`.

---

## Iteration Semantics

See `PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) for the full iteration loop.
The schema fields that support iteration (`iteration.max`, `iteration.current`,
`SubPartStatus` transitions) are defined in [SubPart Fields](#subpart-fields) above.

### Session IDs in CurrentState

All session IDs live in the **in-memory `CurrentState`** (ref.ap.K3vNzHqR8wYm5pJdL2fXa.E) as a
`sessionIds` array on each sub-part — no separate `session_ids/` directories. The harness
appends a new entry to the in-memory state each time a session is created, then flushes to
`current_state.json` on disk. The last element is the current session. Each entry follows the
[Session Record Schema](#session-record-schema--apmwzgc1hykvwu3ijqbtew4e).

```json
{
  "name": "impl",
  "role": "IMPLEMENTOR",
  "agentType": "ClaudeCode",
  "model": "opus",
  "status": "IN_PROGRESS",
  "sessionIds": [
    {
      "handshakeGuid": "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "agentSession": { "id": "77d5b7ea-cf04-453b-8867-162404763e18" },
      "agentType": "ClaudeCode",
      "model": "sonnet",
      "timestamp": "2026-03-10T15:30:00Z"
    },
    {
      "handshakeGuid": "handshake.f9e8d7c6-b5a4-3210-fedc-ba9876543210",
      "agentSession": { "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890" },
      "agentType": "ClaudeCode",
      "model": "sonnet",
      "timestamp": "2026-03-10T16:45:00Z"
    }
  ]
}
```

This applies to **both** execution sub-parts and planning sub-parts — all state in one
in-memory object (flushed to one file).

### Session Record Schema / ap.mwzGc1hYkVwu3IJQbTeW4.E

Each entry in the `sessionIds` array has the following structure:

| Field | Required | Description |
|-------|----------|-------------|
| `handshakeGuid` | yes | The harness-generated GUID for this session (`handshake.${UUID}`). Our identifier — used in all communication. |
| `agentSession` | yes | Sub-object containing the agent's session identity. |
| `agentSession.id` | yes | The agent's internal session ID (e.g., Claude Code JSONL filename UUID). Used for V2 `--resume` (ref.ap.LX1GCIjv6LgmM7AJFas20.E). |
| `agentType` | yes | Which agent implementation (e.g., `"ClaudeCode"`). |
| `model` | yes | The **actual model name** used for this session (e.g., `"sonnet"`, `"opus"`, `"glm-5"`). Must match the sub-part's `model` field. Never a tier name like `"BudgetHigh"` — critical for V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E). |
| `timestamp` | yes | ISO-8601 timestamp of session creation. |

<!-- V2+ NOTE: When additional agent types are supported, the `agentSession` sub-object can be
     extended with additional fields (e.g., `agentSession.path` for path-based agents). This is
     a trivial additive change — no existing field removal or OR-branch needed. -->

The last element in the `sessionIds` array is the current session.

### Sub-Parts Without Iteration

Sub-parts that lack an `iteration` field execute exactly **once**. These are typically
implementors or one-shot tasks.

### Agent Session & PUBLIC.md Lifecycle

One TMUX session per sub-part — kept alive across iterations (see Hard Constraints in
[high-level.md](../high-level.md)). PUBLIC.md is a single file per sub-part, **overwritten**
each iteration — the reviewer always sees the **current** version, not previous iterations.
History is tracked via `GitCommitStrategy` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) — prior versions
are recoverable from git log.
See [ai-out-directory.md](ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) for
PUBLIC.md writing guidelines and visibility rules.

---

## CurrentState Mutation & Persistence Timing

All mutations happen on the **in-memory `CurrentState` object** (ref.ap.K3vNzHqR8wYm5pJdL2fXa.E).
After every mutation, the full state is flushed to `current_state.json` on disk. This provides
maximum durability and enables V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E) to lose minimal
state on crash.

**Mutation + flush triggers:**

These triggers apply to **all** parts in the `parts` array — planning and execution alike.
The `PartExecutor` mutates the in-memory `CurrentState` identically regardless of whether it
is running a planning-phase part or an execution part.

| Event | In-memory mutation | Mutated by |
|-------|-------------------|------------|
| Agent spawned | `status` → `IN_PROGRESS`, new entry in `sessionIds` | `PartExecutor` (after spawn + session ID resolution) |
| Agent signals `done` (any result) | Reviewer `PASS`: reviewer + doer both → `COMPLETED`. Doer-only `COMPLETED`: doer → `COMPLETED`. Reviewer `NEEDS_ITERATION`: stays `IN_PROGRESS`, `iteration.current` incremented. Doer `COMPLETED` in doer+reviewer: doer stays `IN_PROGRESS` (internal event, no status change). | `PartExecutor` (after processing `AgentSignal.Done`) |
| Agent signals `fail-workflow` | `status` → `FAILED` | `PartExecutor` (after processing `AgentSignal.FailWorkflow`) |
| Agent crash detected | `status` → `FAILED` | `PartExecutor` (after health-aware await loop returns `Crashed`) |
| With-planning workflow init | `CurrentState` created with `parts` containing the planning part (`phase: "planning"`) from workflow JSON `planningParts` | `TicketShepherdCreator` |
| Plan conversion | Execution parts (`phase: "execution"`) appended to `parts` array from `plan_flow.json`; planning part retained at index 0 | `convertPlanToExecutionParts()` |
| Straightforward workflow init | `CurrentState` created with `parts` (all `phase: "execution"`) from workflow JSON | `TicketShepherdCreator` |

Each disk flush is a **full file rewrite** (atomic write to temp file + rename). No partial
updates, no append. Simple and corruption-resistant.
