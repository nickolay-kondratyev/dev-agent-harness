# ContextForAgentProvider / ap.9HksYVzl1KkR9E1L2x8Tx.E

Assembles instruction files for agents. Each agent receives a single Markdown file containing
everything it needs — role definition, ticket, shared context, prior agent outputs, and
communication tooling. The provider is the **single place** that decides what each agent sees.

Renamed from `ContextProvider` to `ContextForAgentProvider` to avoid ambiguity — "context" is
overloaded in this codebase (`ShepherdContext`, etc.).

---

## Why It Exists

Agents run in isolated TMUX sessions with no shared memory. Their only input is the instruction
file sent via `send-keys`. Getting the right content into that file — and keeping irrelevant
content out — is critical for agent effectiveness. The provider centralizes this assembly so
`PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) doesn't need to know the concatenation rules.

---

## Interface

```kotlin
interface ContextForAgentProvider {

    /** Assembles instruction file for a doer execution agent. */
    suspend fun assembleDoerInstructions(request: DoerInstructionRequest): Path

    /** Assembles instruction file for a reviewer execution agent. */
    suspend fun assembleReviewerInstructions(request: ReviewerInstructionRequest): Path

    /** Assembles instruction file for the PLANNER agent. */
    suspend fun assemblePlannerInstructions(request: PlannerInstructionRequest): Path

    /** Assembles instruction file for the PLAN_REVIEWER agent. */
    suspend fun assemblePlanReviewerInstructions(request: PlanReviewerInstructionRequest): Path
}
```

Four methods — one per role. Each agent type has different content needs, and the type system
makes that explicit. No boolean flags, no `when(agentKind)` branching inside a single method.
Each request type carries only the fields relevant to its role (e.g., `feedbackDir` only on
`ReviewerInstructionRequest`, `reviewerPublicMdPath` only on `DoerInstructionRequest`).

---

## Instruction File Content — By Agent Type

Each role has a dedicated template method (`buildDoerSections`, `buildReviewerSections`, etc.)
that reads linearly with no role-dispatching conditionals. The only conditionals within a
template are state-dependent (e.g., `iterationNumber > 1`).

### Doer — ap.5N6TJ1MKDHCG01cJwTMFk.E

Concatenation order (via `assembleDoerInstructions` / `DoerInstructionRequest`):

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | `RoleDefinition.filePath` — the full `.md` file from `$TICKET_SHEPHERD_AGENTS_DIR` | The role file IS the system-level instruction for the agent |
| 2 | **Part context** | Part `name` and `description` from `current_state.json` | Tells the agent which part of the workflow it is executing |
| 3 | **Ticket** | The ticket markdown file (path from CLI `--ticket`) | Full content including frontmatter |
| 4 | **PLAN.md** (with-planning only) | `shared/plan/PLAN.md` | Human-readable plan — only present for `with-planning` workflows. |
| 5 | **Prior PUBLIC.md files** | See [Visibility Rules](#visibility-rules) below | Pointers to relevant prior outputs |
| 7 | **Iteration feedback** (iteration > 1) | Reviewer's `PUBLIC.md` for this part + pushback guidance | What the reviewer found lacking. See [Doer Pushback Guidance](#doer-pushback-guidance--iteration-feedback). |
| 7a | **Per-feedback-item instructions** (inner loop) | Single feedback file content + resolution marker instructions + feedback file path | Assembled per feedback item during the inner feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E). |
| 7b | **WHY-NOT reminder** (all iterations) | Static text | Brief reminder to place WHY-NOT comments. See ref.ap.kmiKk7vECiNSpJjAXYMyE.E. |
| 8 | **PUBLIC.md output path** | Computed by provider | Tells the agent where to write its output |
| 9 | **PUBLIC.md writing guidelines** | Static text | Agent work log: decisions + rationale, what was done, codebase discoveries. |
| 10 | **Callback script usage** | Static help text (signal scripts), wrapped in compaction-survival tags | Shows `done completed` for doers |

### Reviewer

Concatenation order (via `assembleReviewerInstructions` / `ReviewerInstructionRequest`):

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | `RoleDefinition.filePath` — the full `.md` file from `$TICKET_SHEPHERD_AGENTS_DIR` | The role file IS the system-level instruction for the agent |
| 2 | **Part context** | Part `name` and `description` from `current_state.json` | Tells the agent which part of the workflow it is executing |
| 3 | **Ticket** | The ticket markdown file (path from CLI `--ticket`) | Full content including frontmatter |
| 4 | **PLAN.md** (with-planning only) | `shared/plan/PLAN.md` | Human-readable plan — only present for `with-planning` workflows. |
| 5 | **Prior PUBLIC.md files** | See [Visibility Rules](#visibility-rules) below | Pointers to relevant prior outputs |
| 6 | **Doer output for review** | Doer's current `PUBLIC.md` for this part | The artifact being reviewed |
| 6a | **Structured feedback format** | Static text | Reviewer must follow structured feedback format (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) on `needs_iteration` |
| 6b | **Addressed feedback** (iteration > 1) | `__feedback/addressed/*.md` | What the doer addressed. Verify fixes are correct. See Granular Feedback Loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E). |
| 6c | **Rejected feedback** (iteration > 1) | `__feedback/rejected/*.md` | Items where the reviewer previously accepted rejection reasoning. |
| 6d | **Remaining optional feedback** (iteration > 1) | `__feedback/pending/optional__*.md` | Optional items the doer chose to skip. |
| 6e | **Feedback writing instructions** | Static text | How to write new feedback files to `__feedback/pending/` with severity filename prefix. |
| 7 | **PUBLIC.md output path** | Computed by provider | Tells the agent where to write its output |
| 8 | **PUBLIC.md writing guidelines** | Static text | Agent work log: decisions + rationale, review verdicts, codebase discoveries. |
| 9 | **Callback script usage** | Static help text (signal scripts), wrapped in compaction-survival tags | Shows `done pass` and `done needs_iteration` for reviewers |

### Planner

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | PLANNER role file from `$TICKET_SHEPHERD_AGENTS_DIR` | |
| 2 | **Ticket** | The ticket markdown file | |
| 3 | **Role catalog** | All `RoleDefinition` entries — name + description + description_long | So planner can assign roles to sub-parts |
| 4 | **Available agent types & models** | Static text — lists supported `agentType` values and `model` options per type | Planner must assign `agentType` + `model` per sub-part (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E). V1: `ClaudeCode` only, models: `opus` (high), `sonnet` (budget-high). |
| 5 | **Plan format instructions** | Static text — JSON schema for `plan.json` | Must match schema in ref.ap.56azZbk7lAMll0D4Ot2G0.E. Planner must set `loadsPlan: true` on at least one implementor sub-part. |
| 6 | **Reviewer feedback** (iteration > 1) | PLAN_REVIEWER's `PUBLIC.md` | What the plan reviewer found lacking — absent on first iteration |
| 7 | **plan.json output path** | `harness_private/plan.json` (absolute path) | |
| 8 | **PLAN.md output path** | `shared/plan/PLAN.md` (absolute path) | Human-readable plan — fed to implementor sub-parts with `loadsPlan: true` |
| 9 | **PUBLIC.md output path** | `planning/${planner_sub_part}/comm/out/PUBLIC.md` | Planner's rationale and decisions — reviewed by PLAN_REVIEWER |
| 10 | **PUBLIC.md writing guidelines** | Static text | Same as execution agent |
| 11 | **Callback script usage** | Same as execution agent + `validate-plan` query | Includes `callback_shepherd.query.sh validate-plan` with instruction to validate `plan.json` before calling `done`. See ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E. |

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
| 10 | **Callback script usage** | Same as execution agent + `validate-plan` query | Includes `callback_shepherd.query.sh validate-plan` with instruction to validate `plan.json` before signaling `pass`. See ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E. |

---

## Visibility Rules

### Upstream Guarantee: PUBLIC.md Validated Before Assembly

`ContextForAgentProvider` assumes that all referenced `PUBLIC.md` files exist and are
non-empty. This is guaranteed by the executor's PUBLIC.md validation step
(ref.ap.THDW9SHzs1x2JN9YP9OYU.E) — performed after every `done` signal, before the
executor proceeds to assemble the next agent's instructions. The provider does **not**
perform its own existence check — that would be redundant defense-in-depth that masks
upstream bugs.

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

When a reviewer signals `needs_iteration`, its `PUBLIC.md` should follow a structured format.
Free-form feedback wastes iteration budget — the doer may misinterpret vague critique or
miss actionable items entirely. A structured contract ensures productive iterations.

### Enforcement Boundary

**Harness-enforced:** PUBLIC.md must exist and be non-empty after every `done` signal
(including `needs_iteration`). This is validated by the executor
(ref.ap.THDW9SHzs1x2JN9YP9OYU.E) — missing or empty PUBLIC.md triggers re-instruction,
then hard failure.

**Guidance only (not harness-validated):** The structured format below (## Issues,
## Acceptance Criteria, etc.) is delivered to the reviewer as instruction text. The harness
does **not** parse or validate the markdown structure. This is a deliberate KISS choice —
parsing markdown headings would be fragile and over-engineered for V1. The format is
enforced socially: the doer's instructions reference it, creating a feedback loop.

**Granular feedback items:** In addition to PUBLIC.md, the reviewer writes individual
actionable issues as separate markdown files to `__feedback/pending/` with severity filename
prefixes (`critical__`, `important__`, `optional__`)
(ref.ap.3Hskx3JzhDlixTnvYxclk.E). The harness feeds these to the doer one at a time via
the inner feedback loop. The doer writes a `## Resolution: ADDRESSED` or
`## Resolution: REJECTED` marker; the harness reads it and moves the file accordingly.
Rejections trigger a bounded per-item negotiation with the reviewer (at most 2 disagreement
rounds). Full spec: [`granular-feedback-loop.md`](../plan/granular-feedback-loop.md)
(ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

### Required PUBLIC.md Format on `needs_iteration`

The reviewer's `PUBLIC.md` should include these sections when the verdict is `needs_iteration`:

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
- `should-fix` — does not block `pass` alone, but multiple pending `should-fix` items may.

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

Two scripts on your $PATH — one for fire-and-forget signals, one for queries that return data.

### Signals (fire-and-forget — ignore stdout):

**Payload ACK — MUST do first when you receive a wrapped payload:**
When you receive input wrapped in `<payload_from_shepherd_must_ack>` XML tags, you MUST
call the command in the `MUST_ACK_BEFORE_PROCEEDING` attribute BEFORE processing the
payload content:
`callback_shepherd.signal.sh ack-payload <payload_id>`
The `payload_id` and exact command are in the XML wrapper — copy it exactly.

When you complete your task:
`callback_shepherd.signal.sh done completed`        (if you are a doer)
`callback_shepherd.signal.sh done pass`             (if you are a reviewer and work passes)
`callback_shepherd.signal.sh done needs_iteration`  (if you are a reviewer and work needs changes)

If you have a question for the human:
`callback_shepherd.signal.sh user-question "Your question here"`
Wait for the answer — it will arrive via your input.

If you hit an unrecoverable error:
`callback_shepherd.signal.sh fail-workflow "Reason for failure"`

Health ping acknowledgment (when asked):
`callback_shepherd.signal.sh ping-ack`

### Queries (read the response from stdout):

Validate plan before signaling done:
`callback_shepherd.query.sh validate-plan /absolute/path/to/plan.json`
</critical_to_keep_through_compaction>
```

The provider inserts the **correct result value** for the agent's role (doer vs. reviewer)
so the agent doesn't have to figure out which values apply to it.

**Planning-phase agents** (PLANNER and PLAN_REVIEWER) receive the full compaction-survival
block above, which includes the `callback_shepherd.query.sh validate-plan` instruction in the
Queries section. This ensures both the planner (after writing `plan.json`) and the plan
reviewer (before approving) validate the plan schema, catching structural errors before
`convertPlanToExecutionParts` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) runs.

**Execution-phase agents** receive the same block but the provider **omits the Queries
section** — execution agents have no query endpoints to call.

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
(`PartExecutor` ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) sends the path to the agent via TMUX `send-keys`.

---

## Ownership and Wiring

- **Created by** `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
- **Used by** `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) directly during instruction assembly
- **Depends on**: `.ai_out/` directory schema (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) for path resolution

---

## What It Does NOT Do

- **Does not decide which agent to spawn** — that's TicketShepherd walking the workflow
- **Does not send the file to the agent** — that's the `PartExecutor` via TMUX `send-keys`
- **Does not validate PUBLIC.md existence** — guaranteed by executor's upstream validation (ref.ap.THDW9SHzs1x2JN9YP9OYU.E)
