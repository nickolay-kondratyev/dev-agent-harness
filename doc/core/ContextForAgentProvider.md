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
     * Writes to the sub-part's comm/in/instructions.md in .ai_out/.
     * Returns the path to the written file.
     */
    suspend fun assembleExecutionAgentInstructions(request: ExecutionAgentInstructionRequest): Path

    /**
     * Assembles instruction file for the PLANNER agent.
     * Writes to the sub-part's comm/in/instructions.md in .ai_out/.
     * Returns the path to the written file.
     */
    suspend fun assemblePlannerInstructions(request: PlannerInstructionRequest): Path

    /**
     * Assembles instruction file for the PLAN_REVIEWER agent.
     * Writes to the sub-part's comm/in/instructions.md in .ai_out/.
     * Returns the path to the written file.
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
| 7 | **Iteration context** (reviewer only) | Doer's current `PUBLIC.md` for this part + structured feedback format + WHY-NOT guidance | The artifact being reviewed. Reviewer must follow structured feedback format (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) on `needs_iteration` and suggest WHY-NOT placements (ref.ap.kmiKk7vECiNSpJjAXYMyE.E). |
| 8 | **Iteration feedback** (doer on iteration > 1) | Reviewer's `PUBLIC.md` for this part + pushback guidance + WHY-NOT protocol | What the reviewer found lacking. See [Doer Pushback Guidance](#doer-pushback-guidance--iteration-feedback) and [WHY-NOT Protocol](#why-not-comments-protocol--apkmikk7vecinsppjjaxymyee). |
| 8b | **WHY-NOT reminder** (doer, all iterations) | Static text | Brief reminder to place WHY-NOT comments when discovering dead-end approaches. See ref.ap.kmiKk7vECiNSpJjAXYMyE.E. |
| 9 | **PUBLIC.md output path** | Computed by provider | Tells the agent where to write its output |
| 10 | **PUBLIC.md writing guidelines** | Static text | Agent work log: decisions + rationale, what was done, review verdicts. No duplication of plan/SHARED_CONTEXT.md content. |
| 11 | **SHARED_CONTEXT.md writing guidelines** | Static text | Shared knowledge base: codebase discoveries, anchor points of interest, cross-cutting constraints, patterns observed. Mutable — update in place, don't append duplicates. See [ai-out-directory.md](../schema/ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E). |
| 12 | **Callback script usage** | Static help text, wrapped in `<critical_to_keep_through_compaction>` | Survives Claude Code context compaction |

### Planner

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | PLANNER role file from `$TICKET_SHEPHERD_AGENTS_DIR` | |
| 2 | **Ticket** | The ticket markdown file | |
| 3 | **SHARED_CONTEXT.md** | `.ai_out/${branch}/shared/SHARED_CONTEXT.md` | Always included (may be empty on first iteration). Planner can read and modify it. |
| 4 | **Role catalog** | All `RoleDefinition` entries — name + description + description_long | So planner can assign roles to sub-parts |
| 5 | **Available agent types & models** | Static text — lists supported `agentType` values and `model` options per type | Planner must assign `agentType` + `model` per sub-part (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E). V1: `ClaudeCode` only, models: `opus` (high), `sonnet` (budget-high). |
| 6 | **Plan format instructions** | Static text — JSON schema for `plan.json` | Must match schema in ref.ap.56azZbk7lAMll0D4Ot2G0.E. Planner must set `loadsPlan: true` on at least one implementor sub-part. |
| 7 | **Reviewer feedback** (iteration > 1) | PLAN_REVIEWER's `PUBLIC.md` | What the plan reviewer found lacking — absent on first iteration |
| 8 | **plan.json output path** | `harness_private/plan.json` (absolute path) | |
| 9 | **PLAN.md output path** | `shared/plan/PLAN.md` (absolute path) | Human-readable plan — fed to implementor sub-parts with `loadsPlan: true` |
| 10 | **PUBLIC.md output path** | `planning/${planner_sub_part}/comm/out/PUBLIC.md` | Planner's rationale and decisions — reviewed by PLAN_REVIEWER |
| 11 | **PUBLIC.md writing guidelines** | Static text | Same as execution agent |
| 12 | **Callback script usage** | Same as execution agent + `validate-plan` | Includes `callback_shepherd.validate-plan.sh` with instruction to validate `plan.json` before calling `done`. See ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E. |

### Plan Reviewer

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | PLAN_REVIEWER role file from `$TICKET_SHEPHERD_AGENTS_DIR` | |
| 2 | **Ticket** | The ticket markdown file | |
| 3 | **plan.json content** | Read from `harness_private/plan.json` | Injected by provider — not in `shared/` |
| 4 | **PLAN.md content** | Read from `shared/plan/PLAN.md` | |
| 5 | **Available agent types & models** | Same as planner receives | Reference for validating planner's `agentType` + `model` assignments |
| 6 | **Planner's PUBLIC.md** | `planning/${planner_sub_part}/comm/out/PUBLIC.md` | Planner's rationale |
| 7 | **Iteration feedback** (iteration > 1) | Plan reviewer's own prior `PUBLIC.md` | What it previously flagged |
| 8 | **PUBLIC.md output path** | Computed by provider | `planning/${plan_review_sub_part}/comm/out/PUBLIC.md` — tells the reviewer where to write its output |
| 9 | **PUBLIC.md writing guidelines** | Static text | Same as execution agent |
| 10 | **Callback script usage** | Same as execution agent + `validate-plan` | Includes `callback_shepherd.validate-plan.sh` with instruction to validate `plan.json` before signaling `pass`. See ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E. |

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

## Structured Reviewer Feedback Contract / ap.EslyJMFQq8BBrFXCzYw5P.E

When a reviewer signals `needs_iteration`, its `PUBLIC.md` must follow a structured format.
Free-form feedback wastes iteration budget — the doer may misinterpret vague critique or
miss actionable items entirely. A structured contract ensures productive iterations.

### Required PUBLIC.md Format on `needs_iteration`

The reviewer's `PUBLIC.md` must include these sections when the verdict is `needs_iteration`:

```markdown
## Verdict: needs_iteration

## Issues
- [ ] <issue-1>: <description> | Severity: must-fix | File(s): <path>
- [ ] <issue-2>: <description> | Severity: should-fix | File(s): <path>

## WHY-NOT Pitfalls to Document
<any approaches the doer tried that should get WHY-NOT comments — see ref.ap.kmiKk7vECiNSpJjAXYMyE.E>

## Acceptance Criteria for Next Iteration
<concrete checklist the doer must satisfy to get a `pass`>

## What Passed (do NOT regress)
<list of things that are good and must be preserved>
```

**Severity levels:**
- `must-fix` — blocks `pass`. The doer must address this or explicitly push back with reasoning.
- `should-fix` — does not block `pass` alone, but multiple unaddressed `should-fix` items may.

The reviewer instructions (assembled by `ContextForAgentProvider`) include this format as
static guidance text, wrapped in `<critical_to_keep_through_compaction>` tags.

On `pass`, the reviewer's `PUBLIC.md` is free-form — a brief summary of what was reviewed
and why it passes is sufficient.

---

## Doer Pushback Guidance — Iteration Feedback

When a doer receives reviewer feedback on iteration > 1, the instruction file includes
**pushback guidance** as static text alongside the reviewer's `PUBLIC.md`. This guidance
is critical because the doer↔reviewer loop is a dialogue where the doer may legitimately
disagree with the reviewer's feedback.

### Why This Matters

A typical multi-part workflow looks like:

```
part_1 (main implementation and review loop) {
  implementor       ← doer
  reviewer           ← reviewer
}
part_2 (single sub-part) {
  reviewer_with_self_fixing  ← final pass
}
```

The part 2 reviewer (or any future agent reading the code) will not have access to the
iteration dialogue between the implementor and reviewer in part 1. If the implementor
silently accepts bad reviewer feedback, the code degrades. If the implementor silently
rejects good feedback, later reviewers will flag the same issues again.

**The solution**: when the doer disagrees with reviewer feedback, it must **defend the
decision in the code itself** — via comments explaining WHY the reviewer's suggestion was
considered and rejected. This creates a durable record that survives beyond the iteration
loop and prevents future reviewers from re-raising the same points.

### Guidance Text (included in doer instructions on iteration > 1)

```markdown
## Handling Reviewer Feedback

You have received feedback from the reviewer. Address each point:

- **If you agree**: implement the requested changes.
- **If you disagree**: you are empowered to push back, but you MUST defend your decision
  **in the code** using a WHY-NOT comment (see below). This comment is NOT for the
  reviewer — it is for any future reader of this code who might have the same question.
- **Do NOT push back for the sake of pushing back.** Only reject feedback when you
  genuinely believe the reviewer is incorrect or missing context.
- **Document your reasoning in PUBLIC.md**: for each reviewer point, state whether you
  accepted or rejected it and why. This helps the reviewer on the next pass understand
  your decisions without re-reviewing unchanged code.

## WHY-NOT Comments — Durable Pitfall Documentation

When you reject a reviewer suggestion, discover a dead-end approach, or fix a rejected
approach, place a `WHY-NOT` comment at the code location where someone might naturally
attempt the wrong approach:

  // WHY-NOT(YYYY-MM-DD): Don't use <approach> here — <concise reason>.
  // <what constraint or failure makes it wrong>. Revisit if <conditions change>.

The date stamp keeps the comment honest — a stale WHY-NOT with changed circumstances
invites scrutiny rather than blind obedience.

WHY-NOT comments are NOT carved in stone. They represent the best understanding of
constraints at the time they were written. A future agent or human may legitimately
override one if circumstances change. The comment gives them context to make that
decision consciously rather than blindly.

Three sources trigger WHY-NOT comments:
1. Reviewer rejects an approach → doer documents why it was wrong at the code site
2. Doer pushes back on reviewer feedback → doer places WHY-NOT to make reasoning
   visible to the reviewer and future readers
3. Doer self-discovers a dead end → doer documents the pitfall inline at the
   point where someone would naturally try the failed approach
```

This guidance is wrapped in `<critical_to_keep_through_compaction>` tags alongside the
callback script usage to survive context compaction.

---

## WHY-NOT Comments Protocol / ap.kmiKk7vECiNSpJjAXYMyE.E

WHY-NOT comments are **durable pitfall documentation** placed inline at code locations where
someone might naturally attempt a wrong approach. They survive beyond the current run —
future agents, future tries, and future humans all benefit. They live exactly where someone
would make the mistake.

### Format

```kotlin
// WHY-NOT(2026-03-13): Don't use Ktor's WebSocket here — the agent TMUX
// session doesn't support persistent connections; HTTP POST + TMUX send-keys
// is the only reliable delivery path. Revisit if agent protocol changes.
withTmuxDelivery(paneTarget, content)
```

The date stamp keeps comments honest — a WHY-NOT from months ago with changed circumstances
invites re-evaluation rather than blind obedience.

### Three Sources

| Source | When | Example |
|--------|------|---------|
| **Reviewer → Doer** | Reviewer rejects an approach, doer fixes and documents why it was wrong | Reviewer flags `runBlocking` misuse → doer switches to `withContext` and adds WHY-NOT explaining the deadlock risk |
| **Doer → Reviewer** (pushback) | Doer disagrees with reviewer suggestion, places WHY-NOT to make reasoning visible | Doer keeps `delay()` over `select{}` for a specific edge case and explains why at the call site |
| **Doer self-discovered** | Doer hits a dead end during implementation, pivots, and documents the pitfall | Doer tried temp-file-based port discovery, hit race conditions, switched to env var — documents at the port resolution code |

### Not Immutable

WHY-NOT comments represent the **best understanding of constraints at the time they were
written**. A future agent or human may legitimately override one if circumstances change
(new dependency version, different runtime, relaxed constraint). The comment gives them the
context to make that decision **consciously** rather than blindly repeating a past mistake.

### Reviewer Responsibility

When a reviewer signals `needs_iteration`, its structured feedback
(ref.ap.EslyJMFQq8BBrFXCzYw5P.E) includes a `## WHY-NOT Pitfalls to Document` section
listing approaches that should receive WHY-NOT comments. On the next review pass, the
reviewer validates that WHY-NOT comments were placed for previously rejected approaches.
Missing WHY-NOT for a rejected approach is a valid `should-fix` issue.

### Integration with Agent Instructions

The WHY-NOT protocol guidance is included in:
- **Doer instructions** (iteration > 1) — as part of the pushback guidance text
  (see [Doer Pushback Guidance](#doer-pushback-guidance--iteration-feedback))
- **Doer instructions** (all iterations) — brief reminder to place WHY-NOT comments when
  discovering dead-end approaches during implementation
- **Reviewer instructions** — guidance to suggest WHY-NOT placements in the structured
  feedback and validate them on subsequent passes

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
`callback_shepherd.validate-plan.sh /absolute/path/to/harness_private/plan.json`
Prints validation result to stdout. Fix any errors before calling done.
```

This ensures both the planner (after writing `plan.json`) and the plan reviewer (before
approving) validate the plan schema, catching structural errors before
`convertPlanToExecutionParts` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) runs.

---

## Output Location

Instruction files are written to the sub-part's `comm/in/instructions.md` inside `.ai_out/`:

```
.ai_out/${git_branch}/execution/${part_name}/${sub_part}/comm/in/instructions.md
.ai_out/${git_branch}/planning/${sub_part}/comm/in/instructions.md
```

Instructions are **overwritten** on each iteration — git history preserves prior versions.
This means no cleanup is needed and the full communication is git-tracked alongside agent
outputs (`comm/out/PUBLIC.md`).

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
