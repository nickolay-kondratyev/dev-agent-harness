# ContextForAgentProvider / ap.9HksYVzl1KkR9E1L2x8Tx.E

Assembles instruction files for agents. Each agent receives a single Markdown file containing
everything it needs — role definition, ticket, shared context, prior agent outputs, and
communication tooling. The provider is the **single place** that decides what each agent sees.

Renamed from `ContextProvider` to `ContextForAgentProvider` to avoid ambiguity — "context" is
overloaded in this codebase (`ShepherdContext`, `SHARED_CONTEXT.md`, etc.).

---

## Why It Exists

Agents run in isolated TMUX sessions with no shared memory. Their only input is the instruction
file sent via `send-keys`. Getting the right content into that file — and keeping irrelevant
content out — is critical for agent effectiveness. The provider centralizes this assembly so
callers (`SubPartInstructionProvider` implementations) don't need to know the concatenation rules.

---

## Interface

```kotlin
interface ContextForAgentProvider {

    /**
     * Assembles instruction file for an execution agent (doer or reviewer).
     * Returns the path to the written temp file.
     */
    suspend fun assembleExecutionAgentInstructions(request: ExecutionAgentInstructionRequest): Path

    /**
     * Assembles instruction file for the PLANNER agent.
     * Returns the path to the written temp file.
     */
    suspend fun assemblePlannerInstructions(request: PlannerInstructionRequest): Path

    /**
     * Assembles instruction file for the PLAN_REVIEWER agent.
     * Returns the path to the written temp file.
     */
    suspend fun assemblePlanReviewerInstructions(request: PlanReviewerInstructionRequest): Path
}
```

Three methods, not one generic method. Each agent type has different content needs, and the
type system should make that explicit. No `when(agentKind)` branching inside a single method.

---

## Instruction File Content — By Agent Type

### Execution Agent (Doer / Reviewer) — ap.5N6TJ1MKDHCG01cJwTMFk.E

Concatenation order:

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | `RoleDefinition.filePath` — the full `.md` file from `$TICKET_SHEPHERD_AGENTS_DIR` | The role file IS the system-level instruction for the agent |
| 2 | **Part context** | Part `name` and `description` from `current_state.json` | Tells the agent which part of the workflow it is executing and what that part is about. Essential for multi-part workflows where the ticket describes the whole task but this agent handles one piece. |
| 3 | **Ticket** | The ticket markdown file (path from CLI `--ticket`) | Full content including frontmatter |
| 4 | **SHARED_CONTEXT.md** | `.ai_out/${branch}/shared/SHARED_CONTEXT.md` | May be empty on first agent. Agent can modify it. |
| 5 | **PLAN.md** (with-planning only) | `shared/plan/PLAN.md` | Human-readable plan — big picture context. Only present for `with-planning` workflows. |
| 6 | **Prior PUBLIC.md files** | See [Visibility Rules](#visibility-rules) below | Pointers to relevant prior outputs |
| 7 | **Iteration context** (reviewer only) | Doer's current `PUBLIC.md` for this part | The artifact being reviewed |
| 8 | **Iteration feedback** (doer on iteration > 1) | Reviewer's `PUBLIC.md` for this part | What the reviewer found lacking |
| 9 | **PUBLIC.md output path** | Computed by provider | Tells the agent where to write its output |
| 10 | **PUBLIC.md writing guidelines** | Static text | Agent work log: decisions + rationale, what was done, review verdicts. No duplication of plan/SHARED_CONTEXT.md content. |
| 11 | **SHARED_CONTEXT.md writing guidelines** | Static text | Shared knowledge base: codebase discoveries, anchor points of interest, cross-cutting constraints, patterns observed. Mutable — update in place, don't append duplicates. See [ai-out-directory.md](../schema/ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E). |
| 12 | **Callback script usage** | Static help text, wrapped in `<critical_to_keep_through_compaction>` | Survives Claude Code context compaction |

### Planner

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | PLANNER role file from `$TICKET_SHEPHERD_AGENTS_DIR` | |
| 2 | **Ticket** | The ticket markdown file | |
| 3 | **Role catalog** | All `RoleDefinition` entries — name + description + description_long | So planner can assign roles to sub-parts |
| 4 | **Available agent types & models** | Static text — lists supported `agentType` values and `model` options per type | Planner must assign `agentType` + `model` per sub-part (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E). V1: `ClaudeCode` only, models: `opus` (high), `sonnet` (budget-high). |
| 5 | **Plan format instructions** | Static text — JSON schema for `plan.json` | Must match schema in ref.ap.56azZbk7lAMll0D4Ot2G0.E |
| 6 | **Reviewer feedback** (iteration > 1) | PLAN_REVIEWER's `PUBLIC.md` | What the plan reviewer found lacking — absent on first iteration |
| 7 | **plan.json output path** | `harness_private/plan.json` | |
| 8 | **PLAN.md output path** | `shared/plan/PLAN.md` | Human-readable plan |
| 9 | **PUBLIC.md output path** | `planning/${planner_sub_part}/PUBLIC.md` | Planner's rationale and decisions — reviewed by PLAN_REVIEWER |
| 10 | **PUBLIC.md writing guidelines** | Static text | Same as execution agent |
| 11 | **Callback script usage** | Same as execution agent + `validate-plan` | Includes `callback_shepherd.validate-plan.sh` with instruction to validate `plan.json` before calling `done`. See ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E. |

### Plan Reviewer

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | PLAN_REVIEWER role file from `$TICKET_SHEPHERD_AGENTS_DIR` | |
| 2 | **Ticket** | The ticket markdown file | |
| 3 | **plan.json content** | Read from `harness_private/plan.json` | Injected by provider — not in `shared/` |
| 4 | **PLAN.md content** | Read from `shared/plan/PLAN.md` | |
| 5 | **Planner's PUBLIC.md** | `planning/${planner_sub_part}/PUBLIC.md` | Planner's rationale |
| 6 | **Iteration feedback** (iteration > 1) | Plan reviewer's own prior `PUBLIC.md` | What it previously flagged |
| 7 | **PUBLIC.md output path** | Computed by provider | `planning/${plan_review_sub_part}/PUBLIC.md` — tells the reviewer where to write its output |
| 8 | **PUBLIC.md writing guidelines** | Static text | Same as execution agent |
| 9 | **Callback script usage** | Same as execution agent + `validate-plan` | Includes `callback_shepherd.validate-plan.sh` with instruction to validate `plan.json` before signaling `pass`. See ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E. |

---

## Visibility Rules

### Which Prior PUBLIC.md Files Does an Execution Agent See?

**Rule: all completed prior parts' PUBLIC.md files, plus the current part's peer sub-part.**

Concretely, for a sub-part in part N:
- All sub-parts' `PUBLIC.md` from parts 1 through N-1 (completed parts)
- The peer sub-part's `PUBLIC.md` within the same part (if it exists — e.g., reviewer
  sees doer's output, doer on iteration sees reviewer's feedback)
- **NOT** future parts' outputs (they don't exist yet)

This is deterministic from the workflow position. No heuristics, no "relevance" scoring.

### Planning Phase Visibility

- PLAN_REVIEWER sees planner's `PUBLIC.md`
- Planner on iteration sees plan reviewer's `PUBLIC.md`
- Execution agents do **not** see planning phase `PUBLIC.md` files — the plan itself
  (`PLAN.md` in `shared/plan/`) is sufficient context

---

## Callback Script Help — Compaction Survival

The callback script usage block is wrapped in `<critical_to_keep_through_compaction>` tags:

```markdown
<critical_to_keep_through_compaction>
## Communicating with the Harness

Use these scripts to communicate back to the harness. They are on your $PATH.

### When you complete your task:
`callback_shepherd.done.sh completed`   (if you are a doer)
`callback_shepherd.done.sh pass`        (if you are a reviewer and work passes)
`callback_shepherd.done.sh needs_iteration` (if you are a reviewer and work needs changes)

### If you have a question for the human:
`callback_shepherd.user-question.sh "Your question here"`
Wait for the answer — it will arrive via your input.

### If you hit an unrecoverable error:
`callback_shepherd.fail-workflow.sh "Reason for failure"`

### Health ping acknowledgment (when asked):
`callback_shepherd.ping-ack.sh`
</critical_to_keep_through_compaction>
```

The provider inserts the **correct result value** for the agent's role (doer vs. reviewer)
so the agent doesn't have to figure out which values apply to it.

**Planning-phase agents** (PLANNER and PLAN_REVIEWER) additionally receive:

```markdown
### Validate plan before signaling done:
`callback_shepherd.validate-plan.sh harness_private/plan.json`
Prints validation result to stdout. Fix any errors before calling done.
```

This ensures both the planner (after writing `plan.json`) and the plan reviewer (before
approving) validate the plan schema, catching structural errors before
`convertPlanToExecutionParts` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) runs.

---

## Output Location

Instruction files are written to:
```
$HOME/.shepherd_agent_harness/tmp/agent_comm/<unique_name>.md
```

Unique name format: `instruction_${partName}_${subPartName}_${timestamp}.md`

The provider writes the file and returns its `Path`. The caller
(`PartExecutor` via `SubPartInstructionProvider`) sends the path to the agent via TMUX `send-keys`.

---

## Ownership and Wiring

- **Created by** `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
- **Used by** `SubPartInstructionProvider` (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) implementations —
  called by `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) during instruction assembly
- **Depends on**: `.ai_out/` directory schema (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) for path resolution

---

## What It Does NOT Do

- **Does not decide which agent to spawn** — that's TicketShepherd walking the workflow
- **Does not send the file to the agent** — that's the `PartExecutor` via TMUX `send-keys`
- **Does not read agent output** — PUBLIC.md reading is the executor's/TicketShepherd's concern
- **Does not manage SHARED_CONTEXT.md lifecycle** — agents modify it directly
