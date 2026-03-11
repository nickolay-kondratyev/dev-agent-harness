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
| `iteration` | no | Present only on the reviewer sub-part (second sub-part). Contains `max` (int). |
| `iteration.max` | yes (when `iteration` present) | Maximum number of times the reviewer can loop back to the doer. |
| `sessionIds` | no | Array of session records (runtime, added by harness). Last element = current/resumable session. See [Session Record Schema](#session-record-schema--apmwzgc1hykvwu3ijqbtew4e). |

### plan.json / current_state.json Schema

The plan and execution state share the **same** data structure. `plan.json` is the planner's
raw output (all sub-parts at `NOT_STARTED`). After planning converges, the harness converts
it to `current_state.json` — adding runtime progress (sub-part status, iteration counters).
For straightforward workflows, the harness generates `current_state.json` directly from the
static workflow JSON.

**Example `plan.json` (planner output):**

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

### plan.json → current_state.json Lifecycle

1. **With-planning**: Planner writes `plan.json` to `harness_private/`. PLAN_REVIEWER sees it
   via ContextProvider (not because it's in `shared/`). After planning converges, harness
   converts `plan.json` → `current_state.json` (same structure + runtime status fields).
   `plan.json` is deleted.
2. **Straightforward**: No `plan.json`. Harness generates `current_state.json` directly from
   `config/workflows/straightforward.json`.

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

### At Most 2 Sub-Parts Per Part

Each part has at most 2 sub-parts: a **doer** (first) and an optional **reviewer** (second).
This keeps part execution trivially simple — no multi-reviewer skip logic needed.

- **1 sub-part**: Doer sends `result: "completed"`. Part complete.
- **2 sub-parts**: Doer sends `result: "completed"`, then reviewer runs. On `result: "needs_iteration"`, loop back to doer.

### Execution Flow Within a Part

```
Run doer (result: completed) → Run reviewer
  reviewer result: needs_iteration → Run doer → Run reviewer (counter: 2)
  reviewer result: needs_iteration → Run doer → Run reviewer (counter: 3)
  reviewer result: pass → Part complete
  counter > iteration.max → FailedToConvergeUseCase (user decides whether to grant more)
```

The reviewer's `iteration.max` is an iteration **budget** (not a hard limit — user can override
via `FailedToConvergeUseCase`). On each `needs_iteration` the harness resumes the doer's TMUX
session with new instructions, then resumes the reviewer's session. The reviewer's verdict is
authoritative — no LLM evaluation in this path.

### Session IDs in current_state.json

All session IDs live in `current_state.json` as a `sessionIds` array on each sub-part — no
separate `session_ids/` directories. The harness appends a new entry each time a session is
created. **Resume = use the last element** in the array. Each entry follows the
[Session Record Schema](#session-record-schema--apmwzgc1hykvwu3ijqbtew4e).

```json
{
  "name": "impl",
  "role": "IMPLEMENTOR",
  "sessionIds": [
    {
      "handshake_guid": "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "agent_session_id": "77d5b7ea-cf04-453b-8867-162404763e18",
      "agent_session_path": null,
      "agentType": "ClaudeCode",
      "model": "sonnet",
      "timestamp": "2026-03-10T15:30:00Z"
    },
    {
      "handshake_guid": "handshake.f9e8d7c6-b5a4-3210-fedc-ba9876543210",
      "agent_session_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "agent_session_path": null,
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
| `handshake_guid` | yes | The harness-generated GUID for this session (`handshake.${UUID}`). Our identifier — used in all communication. |
| `agent_session_id` | no | The agent's internal session ID (e.g., Claude Code JSONL filename UUID). Used for `--resume`. Null when `agent_session_path` is used instead. |
| `agent_session_path` | no | Alternative to `agent_session_id` for agents that use paths (e.g., PI). Null when not applicable. |
| `agentType` | yes | Which agent implementation (e.g., `"ClaudeCode"`, `"PI"`). |
| `model` | yes | The model used for this session (e.g., `"sonnet"`, `"glm-4.7-flash"`). Required for resume — cannot resume a session started with one model using a different model. |
| `timestamp` | yes | ISO-8601 timestamp of session creation. |

**Exactly one** of `agent_session_id` or `agent_session_path` must be non-null.
**Resume = use the last element** in the `sessionIds` array. The `agent_session_id` (or
`agent_session_path`) plus `model` from that entry are used for the `--resume` invocation.

### Sub-Parts Without Iteration

Sub-parts that lack an `iteration` field execute exactly **once**. These are typically
implementors or one-shot tasks.

### Agent Session Lifecycle

**One TMUX session per sub-part.** The harness spawns a TMUX session on a sub-part's first run
and keeps it alive across iteration loops. New instructions are delivered via TMUX `send-keys` —
no kill/respawn between iterations. The session is killed only when the **part** completes.

- The agent retains its full conversation history across iterations of the same sub-part.
- The `sessionIds` array on each sub-part tracks session records. The last element is the
  current/resumable session.

### PUBLIC.md Lifecycle

- **Single `PUBLIC.md` per sub-part**, overwritten each iteration. The agent is responsible for
  including relevant context in its output.
- **Trackability via git**: The harness commits via `GitCommitStrategy` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E),
  so the full history of `PUBLIC.md` changes is preserved in git — no need for versioned files.
- The TMUX session stays alive — the agent receives updated instructions via `send-keys` and
  retains its full conversation history across iterations of the same sub-part.
