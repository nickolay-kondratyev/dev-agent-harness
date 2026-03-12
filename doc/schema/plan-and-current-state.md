# Plan & Current-State JSON Schema / ap.56azZbk7lAMll0D4Ot2G0.E

For directory layout and file locations see [`.ai_out/` directory schema](ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

---

## Unified Plan Schema — Parts and Sub-Parts

All workflow JSON files (static workflows **and** planner-generated `plan.json`) use the **same**
parts/sub-parts schema. One parser handles everything.

### SubPart Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | yes | Directory name and identifier (e.g., `"impl"`, `"review"`). Execution order is determined by array position. |
| `role` | yes | Role from the role catalog (`$TICKET_SHEPHERD_AGENTS_DIR/*.md`). |
| `agentType` | no | Agent implementation to use (e.g., `"ClaudeCode"`, `"PI"`). If absent, resolved from role catalog frontmatter. |
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
```

**Part-level status is derived** — no explicit part status field:
- All sub-parts `COMPLETED` → part complete
- Any sub-part `FAILED` → part failed
- First non-`COMPLETED` sub-part → resume point

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

### plan.json / current_state.json Schema

The plan and execution state share the **same** base structure (parts → sub-parts). They differ
in whether runtime fields are present:

| File | Runtime fields | Source |
|------|---------------|--------|
| `plan.json` | **Absent** — no `status`, no `iteration.current`, no `sessionIds` | Planner output |
| `current_state.json` | **Present** — `status` on every sub-part, `iteration.current` on reviewers, `sessionIds` as sessions are created | Harness-managed |

For straightforward workflows, the harness generates `current_state.json` directly from the
static workflow JSON (no `plan.json` involved).

**Example `plan.json` (planner output — no runtime fields):**

```json
{
  "parts": [
    {
      "name": "ui_design",
      "description": "Design the dashboard UI",
      "subParts": [
        { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode" },
        { "name": "review", "role": "UI_REVIEWER", "agentType": "ClaudeCode",
          "iteration": { "max": 3 } }
      ]
    },
    {
      "name": "backend_impl",
      "description": "Implement API endpoints",
      "subParts": [
        { "name": "impl", "role": "IMPLEMENTOR" },
        { "name": "review", "role": "IMPLEMENTATION_REVIEWER",
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
          "status": "NOT_STARTED"
        },
        {
          "name": "review",
          "role": "IMPLEMENTATION_REVIEWER",
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

### plan.json → current_state.json Lifecycle

1. **With-planning**: Planner writes `plan.json` to `harness_private/`. PLAN_REVIEWER sees it
   via `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) (not because it's in `shared/`). After planning converges, harness
   converts `plan.json` → `current_state.json` and deletes `plan.json`.
2. **Straightforward**: No `plan.json`. Harness generates `current_state.json` directly from
   `config/workflows/straightforward.json`.

**Conversion adds these runtime fields:**
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
        { "name": "impl", "role": "IMPLEMENTOR_WITH_SELF_PLAN" },
        { "name": "review", "role": "IMPLEMENTATION_REVIEWER",
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
    { "name": "plan", "role": "PLANNER" },
    { "name": "plan_review", "role": "PLAN_REVIEWER",
      "iteration": { "max": 3 } }
  ],
  "executionPhasesFrom": "plan.json"
}
```

### WorkflowDefinition — Two Modes

A workflow is either **straightforward** (has `parts`) or **with-planning** (has `planningSubParts` +
`executionPhasesFrom`). These are mutually exclusive — exactly one set of fields will be non-null.

- **Straightforward**: `parts` contains the full execution plan (static).
- **With-planning**: `planningSubParts` defines the planning loop (PLANNER ↔ PLAN_REVIEWER).
  `executionPhasesFrom` names the file the planner generates (e.g., `"plan.json"`) in `harness_private/`.
  After planning converges, the harness converts `plan.json` → `current_state.json` and deletes `plan.json`.

---

## Iteration Semantics

See `DoerReviewerPartExecutor` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) for the full iteration loop.
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
| `model` | yes | The model used for this session (e.g., `"sonnet"`, `"glm-4.7-flash"`). |
| `timestamp` | yes | ISO-8601 timestamp of session creation. |

**Exactly one** of `agentSessionId` or `agentSessionPath` must be non-null.
The last element in the `sessionIds` array is the current session.

### Sub-Parts Without Iteration

Sub-parts that lack an `iteration` field execute exactly **once**. These are typically
implementors or one-shot tasks.

### Agent Session & PUBLIC.md Lifecycle

One TMUX session per sub-part — kept alive across iterations (see Hard Constraints in
[high-level.md](../high-level.md)). PUBLIC.md is a single file per sub-part, overwritten
each iteration — history tracked via `GitCommitStrategy` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).
See [ai-out-directory.md](ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) for
PUBLIC.md vs SHARED_CONTEXT.md semantics.
