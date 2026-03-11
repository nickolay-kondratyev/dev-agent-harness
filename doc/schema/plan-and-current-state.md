# Plan & Current-State JSON Schema / ap.56azZbk7lAMll0D4Ot2G0.E

For directory layout and file locations see [`.ai_out/` directory schema](ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

---

## Unified Plan Schema — Parts and Sub-Parts

All workflow JSON files (static workflows **and** planner-generated `plan.json`) use the **same**
parts/sub-parts schema. One parser handles everything.

### SubPart Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | yes | Directory name and identifier (e.g., `"1_impl"`, `"2_review"`). Numbered prefix for execution order. |
| `role` | yes | Role from the role catalog (`$CHAINSAW_AGENTS_DIR/*.md`). |
| `agentType` | no | Agent implementation to use (e.g., `"ClaudeCode"`, `"PI"`). If absent, resolved from role catalog frontmatter. |
| `iteration` | no | Present only on reviewer sub-parts. Contains `max` (int) and `loopsBackTo` (string). |
| `iteration.max` | yes (when `iteration` present) | Maximum number of times **this reviewer** can trigger a loop-back. |
| `iteration.loopsBackTo` | yes (when `iteration` present) | Name of the sub-part to return to on failure (typically the implementor). |
| `sessionIds` | no | Array of session records (runtime, added by harness). Last element = current/resumable session. Each entry: `{ "id": "...", "agentType": "ClaudeCode", "timestamp": "..." }`. |

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
        { "name": "1_impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode" },
        { "name": "2_review", "role": "UI_REVIEWER", "agentType": "ClaudeCode",
          "iteration": { "max": 3, "loopsBackTo": "1_impl" } },
        { "name": "3_security_review", "role": "SECURITY_REVIEWER", "agentType": "PI",
          "iteration": { "max": 2, "loopsBackTo": "1_impl" } }
      ]
    },
    {
      "name": "backend_impl",
      "description": "Implement API endpoints",
      "subParts": [
        { "name": "1_impl", "role": "IMPLEMENTOR" },
        { "name": "2_review", "role": "IMPLEMENTATION_REVIEWER",
          "iteration": { "max": 4, "loopsBackTo": "1_impl" } }
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
        { "name": "1_impl", "role": "IMPLEMENTOR_WITH_SELF_PLAN" },
        { "name": "2_review", "role": "IMPLEMENTATION_REVIEWER",
          "iteration": { "max": 4, "loopsBackTo": "1_impl" } }
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
    { "name": "1_plan", "role": "PLANNER" },
    { "name": "2_plan_review", "role": "PLAN_REVIEWER",
      "iteration": { "max": 3, "loopsBackTo": "1_plan" } }
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

### Sub-Part-Level Iteration

Iteration is defined **per reviewer sub-part**, not per part. Each reviewer has:
- Its own iteration budget (`iteration.max`)
- An explicit loop-back target (`iteration.loopsBackTo`)

### Execution Flow Within a Part

Sub-parts execute **sequentially** in order. When a reviewer sub-part fails (LLM evaluation
determines the review did not pass):

1. The harness re-runs the `loopsBackTo` target (typically the implementor).
2. The harness then re-runs **only the failing reviewer** — intermediate sub-parts that already
   passed are **skipped**.
3. The failing reviewer's iteration counter increments.
4. If the counter exceeds `iteration.max`, the part is considered failed.

**Example — 3 sub-parts:**

```
Part: backend
  1_impl (IMPLEMENTOR)
  2_security (SECURITY_REVIEWER, max: 2, loopsBackTo: 1_impl)
  3_code_review (CODE_REVIEWER, max: 4, loopsBackTo: 1_impl)
```

Execution:
```
Run 1_impl → Run 2_security
  2_security FAIL → Run 1_impl → Run 2_security (counter: 2)
  2_security PASS → Run 3_code_review
  3_code_review FAIL → Run 1_impl → Run 3_code_review (counter: 2; skip 2_security)
  3_code_review PASS → Part complete
```

### Session IDs in current_state.json

All session IDs live in `current_state.json` as a `sessionIds` array on each sub-part — no
separate `session_ids/` directories. The harness appends a new entry each time a session is
created. **Resume = use the last element** in the array.

```json
{
  "name": "1_impl",
  "role": "IMPLEMENTOR",
  "sessionIds": [
    { "id": "77d5b7ea-cf04-453b-8867-162404763e18", "agentType": "ClaudeCode", "timestamp": "2026-03-10T15:30:00Z" },
    { "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "agentType": "ClaudeCode", "timestamp": "2026-03-10T16:45:00Z" }
  ]
}
```

This applies to **both** execution sub-parts and planning sub-parts — all state in one file.

### Sub-Parts Without Iteration

Sub-parts that lack an `iteration` field execute exactly **once**. These are typically
implementors or one-shot tasks.

### Agent Session Lifecycle (V1)

V1 **kills the agent session between iterations** — each sub-part run spawns a fresh session.
Context carries exclusively via files (PUBLIC.md, SHARED_CONTEXT.md) assembled by ContextProvider.

**V2 evolution**: The design should not preclude keeping agent sessions alive across iterations
when the context window has room. The `sessionIds` array already supports multiple sessions per
sub-part, and the harness can decide per-iteration whether to resume or start fresh based on
context usage. See ticket `nid_etxturughxixkl5hmvgazco3j_E`.

### PUBLIC.md Lifecycle

- **Single `PUBLIC.md` per sub-part**, overwritten each iteration. The agent is responsible for
  including relevant context in its output.
- **Trackability via git**: The harness commits between iterations, so the full history of `PUBLIC.md`
  changes is preserved in git — no need for versioned files.
- **Fresh session** (V1 default): The harness starts a new session. The agent picks up context
  purely from PUBLIC.md files and other files assembled by ContextProvider.
- **Resumed session** (V2): The harness resumes the session using the last `sessionIds` entry.
  The agent retains its full conversation history.
