# Plan & Current-State JSON Schema / ap.56azZbk7lAMll0D4Ot2G0.E

For directory layout and file locations see [`.ai_out/` directory schema](ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

---

## Unified Plan Schema — Parts and Sub-Parts

All workflow JSON files (static workflows **and** planner-generated `plan_flow.json`) use the **same**
parts/sub-parts schema. One parser handles everything.

### SubPart Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | yes | Directory name and identifier (e.g., `"impl"`, `"review"`). Execution order is determined by array position. |
| `role` | yes | Role from the role catalog (`$TICKET_SHEPHERD_AGENTS_DIR/*.md`). |
| `agentType` | yes | Agent implementation to use (e.g., `"ClaudeCode"`, `"PI"`). Assigned by the planner (with-planning workflows) or specified in static workflow JSON (straightforward workflows). Never from role definitions — see ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E. |
| `model` | yes | Actual model name (e.g., `"sonnet"`, `"opus"`, `"glm-5"`). Same assignment source as `agentType`. Must be the **actual model name**, never a tier name like `"BudgetHigh"` — required for V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E). |
| `loadsPlan` | no | Boolean. When `true`, the harness includes `PLAN.md` in this sub-part's instruction assembly. The planner must set this on at least one implementor sub-part. Validated harness-side in `convertPlanToExecutionParts` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E). Default: `false`. Note: `PLAN.md` is the human-readable implementation guide; `plan_flow.json` is the workflow definition — these are distinct files with distinct consumers. |
| `status` | runtime | Sub-part execution status. Added by harness when converting to `current_state.json`. See [SubPartStatus](#subpartstatus). |
| `iteration` | no | Present only on the reviewer sub-part (second sub-part). Contains `max` and runtime `current`. |
| `iteration.max` | yes (when `iteration` present) | Maximum number of times the reviewer can loop back to the doer. This is a **budget** — user can override via `FailedToConvergeUseCase`. |
| `iteration.current` | runtime | Current iteration count. Added by harness. Starts at `0`, incremented each time the reviewer signals `needs_iteration`. See [Iteration Counter](#iteration-counter). |
| `sessionIds` | no | Array of session records (runtime, added by harness). Last element = current/resumable session. See [Session Record Schema](#session-record-schema--apmwzgc1hykvwu3ijqbtew4e). |

### SubPartStatus

```kotlin
enum class SubPartStatus {
    NOT_STARTED,   // initial state — sub-part has not been attempted
    IN_PROGRESS,   // agent has been spawned and is working
    COMPLETED,     // agent signaled done successfully (doer: "completed", reviewer: "pass")
    FAILED,        // unrecoverable failure (fail-workflow, crash, failed-to-converge)
}
```

**State transitions:**

```
NOT_STARTED → IN_PROGRESS    (harness spawns agent for this sub-part)
IN_PROGRESS → COMPLETED      (doer: "completed", reviewer: "pass")
IN_PROGRESS → FAILED         (fail-workflow, agent crash, or failed-to-converge)
IN_PROGRESS → IN_PROGRESS    (reviewer: "needs_iteration" — counter increments, status stays)
COMPLETED   → IN_PROGRESS    (doer on re-iteration: reviewer signaled "needs_iteration", doer resumes work)
```

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
     *   - doer:     AgentSignal.Done(DoneResult.COMPLETED)
     *   - reviewer: AgentSignal.Done(DoneResult.PASS)
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

    /**
     * COMPLETED → IN_PROGRESS  (doer sub-part only)
     * Trigger: harness resumes the doer after reviewer signaled NEEDS_ITERATION.
     * No AgentSignal is involved; the executor calls validateCanResumeForIteration()
     * explicitly before re-instructing the idle doer session.
     */
    object ResumeForIteration : SubPartStateTransition()
}
```

#### Validator Functions

Three validator functions cover all five transitions. All throw `IllegalStateException` on an
invalid (status, trigger) combination — crash fast, no silent fallback.

```kotlin
/**
 * Maps an AgentSignal to the corresponding SubPartStateTransition for this status.
 * Covers transitions: Complete, Fail, IterateContinue.
 *
 * @throws IllegalStateException if the (status, signal) pair is not a valid transition.
 * Note: AgentSignal.SelfCompacted does NOT trigger a SubPart status change — it is handled
 *       inside the facade's health-aware await loop and is invisible to PartExecutorImpl.
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
            error("Cannot apply AgentSignal to COMPLETED; call validateCanResumeForIteration() for doer resume")
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

/**
 * Validates that this sub-part is COMPLETED before the harness resumes it for another iteration.
 * Returns SubPartStateTransition.ResumeForIteration on success.
 * @throws IllegalStateException if status != COMPLETED.
 */
fun SubPartStatus.validateCanResumeForIteration(): SubPartStateTransition.ResumeForIteration {
    check(this == COMPLETED) { "resume-for-iteration requires COMPLETED, got $this" }
    return SubPartStateTransition.ResumeForIteration
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
| `harness_private/plan_flow.json` | Harness (machine-parsed) | Strict workflow definition: which agent roles, models, order, and iteration budgets. The harness validates and converts it to `current_state.json`. |
| `shared/plan/PLAN.md` | Implementation agents (LLM-read) | Human-readable guide: clarified requirements, tradeoffs, architecture constraints, affected file paths, design decisions. Fed to `loadsPlan: true` sub-parts. |

**Why both:** `plan_flow.json` tells the harness *how to run the workflow*. `PLAN.md` tells
implementation agents *what to build and how*. They serve different consumers and carry
non-overlapping information — no risk of divergence.

### plan_flow.json / current_state.json Schema

The plan and execution state share the **same** base structure (parts → sub-parts). They differ
in whether runtime fields are present:

| File | Runtime fields | Source |
|------|---------------|--------|
| `plan_flow.json` | **Absent** — no `status`, no `iteration.current`, no `sessionIds` | Planner output |
| `current_state.json` | **Present** — `status` on every sub-part, `iteration.current` on reviewers, `sessionIds` as sessions are created | Harness-managed (see [Persistence Timing](#currentstatejson-persistence-timing)) |

For straightforward workflows, the harness generates `current_state.json` directly from the
static workflow JSON (no `plan_flow.json` involved).

### Planning Part in current_state.json

For `with-planning` workflows, `current_state.json` includes a **`planningPart`** field that
tracks the planning phase using the **exact same schema** as execution parts — same sub-part
fields, same `SubPartStatus` transitions, same `iteration` counter, same `sessionIds` array.

The planning part is treated identically to an execution part in terms of state persistence.
The only differences are:
- It lives at `planningPart` (singular) instead of in the `parts` array
- It is populated from the workflow JSON's `planningSubParts` at workflow init (before any
  agent runs), not from `plan_flow.json` conversion
- After planning completes, `planningPart` stays in `current_state.json` as a historical
  record — it is not deleted

This means:
- Planning sub-part status transitions (`NOT_STARTED` → `IN_PROGRESS` → `COMPLETED`) are
  persisted to disk after every transition
- Planning iteration counters (PLAN_REVIEWER's `iteration.current`) are persisted
- Planning session IDs are persisted in the same `sessionIds` array format
- V2 resume can recover mid-planning state from `current_state.json`

**Example `plan_flow.json` (planner output — no runtime fields):**

```json
{
  "parts": [
    {
      "name": "ui_design",
      "description": "Design the dashboard UI",
      "subParts": [
        { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet", "loadsPlan": true },
        { "name": "review", "role": "UI_REVIEWER", "agentType": "ClaudeCode", "model": "sonnet",
          "iteration": { "max": 3 } }
      ]
    },
    {
      "name": "backend_impl",
      "description": "Implement API endpoints",
      "subParts": [
        { "name": "impl", "role": "IMPLEMENTOR", "agentType": "ClaudeCode", "model": "opus", "loadsPlan": true },
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
      "description": "Design the dashboard UI",
      "subParts": [
        {
          "name": "impl",
          "role": "UI_DESIGNER",
          "agentType": "ClaudeCode",
          "model": "sonnet",
          "status": "COMPLETED",
          "sessionIds": [
            {
              "handshakeGuid": "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "agentSessionId": "77d5b7ea-cf04-453b-8867-162404763e18",
              "agentSessionPath": null,
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
              "agentSessionId": "88e6c8fb-df15-564c-9978-273515874f29",
              "agentSessionPath": null,
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

In this example: `ui_design` doer is done, reviewer is on its second pass (`current: 1` means
one `needs_iteration` has occurred). `backend_impl` hasn't started yet.

**Example `current_state.json` (mid-planning — planning part tracked with same schema):**

```json
{
  "planningPart": {
    "name": "planning",
    "description": "Plan the workflow",
    "subParts": [
      {
        "name": "plan",
        "role": "PLANNER",
        "agentType": "ClaudeCode",
        "model": "opus",
        "status": "COMPLETED",
        "sessionIds": [
          {
            "handshakeGuid": "handshake.c3d4e5f6-a7b8-9012-cdef-123456789012",
            "agentSessionId": "99f7d9gc-eg26-675d-aa89-384626985g30",
            "agentSessionPath": null,
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
            "agentSessionId": "aag8eahd-fh37-786e-bb90-495737a96h41",
            "agentSessionPath": null,
            "agentType": "ClaudeCode",
            "model": "opus",
            "timestamp": "2026-03-10T14:30:00Z"
          }
        ]
      }
    ]
  }
}
```

In this example: the planner completed, the plan reviewer ran once and signaled `needs_iteration`
(`current: 1`), and the planner is about to receive reviewer feedback for a second pass. No
execution `parts` exist yet — they will be populated from `plan_flow.json` after planning converges.

### plan_flow.json → current_state.json Lifecycle

1. **With-planning**:
   a. At workflow init, `TicketShepherdCreator` creates `current_state.json` with the
      `planningPart` populated from the workflow JSON's `planningSubParts` (runtime fields
      added: `status: NOT_STARTED`, `iteration.current: 0`). No `parts` array yet.
   b. During planning, the `PartExecutor` updates `planningPart` sub-part statuses,
      iteration counters, and session IDs — same persistence triggers as execution parts.
   c. Planner writes `plan_flow.json` to `harness_private/` (and `PLAN.md` to `shared/plan/`).
      PLAN_REVIEWER sees `plan_flow.json` via
      `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) (not because it's in `shared/`).
   d. After planning converges, harness converts `plan_flow.json` → adds `parts` array to
      `current_state.json` and deletes `plan_flow.json`. The `planningPart` remains as a
      historical record.
2. **Straightforward**: No `plan_flow.json`, no `planningPart`. Harness generates `current_state.json`
   with `parts` directly from `config/workflows/straightforward.json`.

**Conversion adds these runtime fields to execution parts:**
- `status: "NOT_STARTED"` on every sub-part
- `iteration.current: 0` on every reviewer sub-part (where `iteration` block exists)
- `sessionIds` is absent initially — added when agents are spawned

### Workflow JSON Schema (static workflows)

Static workflow definitions (`config/workflows/*.json`) use the **same** sub-parts structure.

**`config/workflows/straightforward.json`:**

```json
{
  "name": "straightforward",
  "parts": [
    {
      "name": "main",
      "description": "Implement and review",
      "subParts": [
        { "name": "impl", "role": "IMPLEMENTOR_WITH_SELF_PLAN", "agentType": "ClaudeCode", "model": "sonnet" },
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
  "planningSubParts": [
    { "name": "plan", "role": "PLANNER", "agentType": "ClaudeCode", "model": "opus" },
    { "name": "plan_review", "role": "PLAN_REVIEWER", "agentType": "ClaudeCode", "model": "opus",
      "iteration": { "max": 3 } }
  ],
  "executionPhasesFrom": "plan_flow.json"
}
```

Note: `agentType` and `model` on `planningSubParts` are specified in the static workflow JSON —
the planner cannot assign its own agent type. The planner assigns `agentType` and `model` only
for the **execution parts** it generates in `plan_flow.json` (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E).

### WorkflowDefinition — Two Modes

A workflow is either **straightforward** (has `parts`) or **with-planning** (has `planningSubParts` +
`executionPhasesFrom`). These are mutually exclusive — exactly one set of fields will be non-null.

- **Straightforward**: `parts` contains the full execution plan (static).
- **With-planning**: `planningSubParts` defines the planning loop (PLANNER ↔ PLAN_REVIEWER).
  `executionPhasesFrom` names the file the planner generates (e.g., `"plan_flow.json"`) in `harness_private/`.
  After planning converges, the harness converts `plan_flow.json` → `current_state.json` and deletes `plan_flow.json`.

---

## Iteration Semantics

See `PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) for the full iteration loop.
The schema fields that support iteration (`iteration.max`, `iteration.current`,
`SubPartStatus` transitions) are defined in [SubPart Fields](#subpart-fields) above.

### Session IDs in current_state.json

All session IDs live in `current_state.json` as a `sessionIds` array on each sub-part — no
separate `session_ids/` directories. The harness appends a new entry each time a session is
created. The last element is the current session. Each entry follows the
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
      "agentSessionId": "77d5b7ea-cf04-453b-8867-162404763e18",
      "agentSessionPath": null,
      "agentType": "ClaudeCode",
      "model": "sonnet",
      "timestamp": "2026-03-10T15:30:00Z"
    },
    {
      "handshakeGuid": "handshake.f9e8d7c6-b5a4-3210-fedc-ba9876543210",
      "agentSessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "agentSessionPath": null,
      "agentType": "ClaudeCode",
      "model": "sonnet",
      "timestamp": "2026-03-10T16:45:00Z"
    }
  ]
}
```

This applies to **both** execution sub-parts and planning sub-parts — all state in one file.

### Session Record Schema / ap.mwzGc1hYkVwu3IJQbTeW4.E

Each entry in the `sessionIds` array has the following structure:

| Field | Required | Description |
|-------|----------|-------------|
| `handshakeGuid` | yes | The harness-generated GUID for this session (`handshake.${UUID}`). Our identifier — used in all communication. |
| `agentSessionId` | no | The agent's internal session ID (e.g., Claude Code JSONL filename UUID). Null when `agentSessionPath` is used instead. Used for V2 `--resume` (ref.ap.LX1GCIjv6LgmM7AJFas20.E). |
| `agentSessionPath` | no | Alternative to `agentSessionId` for agents that use paths (e.g., PI). Null when not applicable. |
| `agentType` | yes | Which agent implementation (e.g., `"ClaudeCode"`, `"PI"`). |
| `model` | yes | The **actual model name** used for this session (e.g., `"sonnet"`, `"opus"`, `"glm-5"`). Must match the sub-part's `model` field. Never a tier name like `"BudgetHigh"` — critical for V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E). |
| `timestamp` | yes | ISO-8601 timestamp of session creation. |

**Exactly one** of `agentSessionId` or `agentSessionPath` must be non-null.
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

## current_state.json Persistence Timing

The harness writes `current_state.json` to disk **after every state transition and session
record addition**. This provides maximum durability and enables V2 resume
(ref.ap.LX1GCIjv6LgmM7AJFas20.E) to lose minimal state on crash.

**Write triggers:**

These triggers apply to **both** planning sub-parts (in `planningPart`) and execution sub-parts
(in `parts`). The `PartExecutor` writes to `current_state.json` identically regardless of
whether it is running the planning phase or an execution part.

| Event | What changes | Written by |
|-------|-------------|------------|
| Agent spawned | `status` → `IN_PROGRESS`, new entry in `sessionIds` | `PartExecutor` (after spawn + session ID resolution) |
| Agent signals `done` (any result) | `status` → `COMPLETED` (doer/reviewer pass) or stays `IN_PROGRESS` (needs_iteration), `iteration.current` incremented | `PartExecutor` (after processing `AgentSignal.Done`) |
| Agent signals `fail-workflow` | `status` → `FAILED` | `PartExecutor` (after processing `AgentSignal.FailWorkflow`) |
| Agent crash detected | `status` → `FAILED` | `PartExecutor` (after health-aware await loop returns `Crashed`) |
| With-planning workflow init | `current_state.json` created with `planningPart` (from workflow JSON `planningSubParts`), no `parts` yet | `TicketShepherdCreator` |
| Plan conversion | `parts` array added to `current_state.json` from `plan_flow.json`; `planningPart` retained | `convertPlanToExecutionParts()` |
| Straightforward workflow init | `current_state.json` created with `parts` (from workflow JSON), no `planningPart` | `TicketShepherdCreator` |

Each write is a **full file rewrite** (atomic write to temp file + rename). No partial
updates, no append. Simple and corruption-resistant.
