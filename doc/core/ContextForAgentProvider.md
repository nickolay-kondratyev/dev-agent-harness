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

    /**
     * Assembles the instruction file for an agent.
     * The request type encodes the role — no separate role parameter needed.
     * Writes to `request.outputDir/instructions.md`. Returns the written path.
     */
    suspend fun assembleInstructions(request: AgentInstructionRequest): Path
}
```

Single public method. The request type encodes the role — invalid field combinations are
caught at compile time, not at runtime. Adding a new agent role requires only a new
`AgentInstructionRequest` subtype and a plan list.

### AgentInstructionRequest

Each role gets its own subtype with exactly the fields it needs. No nullable role-specific
fields; the type itself is the discriminator.

```kotlin
sealed class AgentInstructionRequest {
    // ── common (all roles) ────────────────────────────────────────────────────
    abstract val roleDefinition: RoleDefinition
    abstract val ticketContent: String
    abstract val iterationNumber: Int
    abstract val outputDir: Path
    abstract val publicMdOutputPath: Path

    // ── execution agents (DOER + REVIEWER) ────────────────────────────────────
    sealed class ExecutionRequest : AgentInstructionRequest() {
        abstract val partName: String
        abstract val partDescription: String
        abstract val planMdPath: Path?           // null → no-planning workflow
        abstract val priorPublicMdPaths: List<Path>

        data class DoerRequest(
            override val roleDefinition: RoleDefinition,
            override val ticketContent: String,
            override val iterationNumber: Int,
            override val outputDir: Path,
            override val publicMdOutputPath: Path,
            override val partName: String,
            override val partDescription: String,
            override val planMdPath: Path?,
            override val priorPublicMdPaths: List<Path>,
            val reviewerPublicMdPath: Path?,     // null on iteration 1
        ) : ExecutionRequest()

        data class ReviewerRequest(
            override val roleDefinition: RoleDefinition,
            override val ticketContent: String,
            override val iterationNumber: Int,
            override val outputDir: Path,
            override val publicMdOutputPath: Path,
            override val partName: String,
            override val partDescription: String,
            override val planMdPath: Path?,
            override val priorPublicMdPaths: List<Path>,
            val doerPublicMdPath: Path,          // always required; non-nullable
            val feedbackDir: Path,               // always required; non-nullable
        ) : ExecutionRequest()
    }

    data class PlannerRequest(
        override val roleDefinition: RoleDefinition,
        override val ticketContent: String,
        override val iterationNumber: Int,
        override val outputDir: Path,
        override val publicMdOutputPath: Path,
        val roleCatalogEntries: List<RoleCatalogEntry>,
        val planReviewerPublicMdPath: Path?,     // null on iteration 1
        val planJsonOutputPath: Path,            // always required; non-nullable
        val planMdOutputPath: Path,              // always required; non-nullable
    ) : AgentInstructionRequest()

    data class PlanReviewerRequest(
        override val roleDefinition: RoleDefinition,
        override val ticketContent: String,
        override val iterationNumber: Int,
        override val outputDir: Path,
        override val publicMdOutputPath: Path,
        val planJsonContent: String,             // always required; non-nullable
        val planMdContent: String,               // always required; non-nullable
        val plannerPublicMdPath: Path,           // always required; non-nullable
        val priorPlanReviewerPublicMdPath: Path?, // null on iteration 1
    ) : AgentInstructionRequest()
}
```

Remaining `null`s are **semantically meaningful optionals** (absent on first iteration or
absent in no-planning workflows) — not "does not apply to this role." The type hierarchy
eliminates the latter category entirely.

---

## Instruction File Content — By Agent Type

Each role defines its section sequence as an `InstructionPlan` — a `List<InstructionSection>`.
A single internal `assembleFromPlan(plan: List<InstructionSection>, request: AgentInstructionRequest)`
walks the plan and renders each section. State-dependent sections (e.g. `iterationNumber > 1`)
are modeled as conditional section types within the plan, not as conditionals scattered across
role-specific template methods.

### Doer — ap.5N6TJ1MKDHCG01cJwTMFk.E

Concatenation order (via `AgentInstructionRequest.ExecutionRequest.DoerRequest`):

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | `RoleDefinition.filePath` — the full `.md` file from `$TICKET_SHEPHERD_AGENTS_DIR` | The role file IS the system-level instruction for the agent |
| 2 | **Part context** | Part `name` and `description` from `current_state.json` | Tells the agent which part of the workflow it is executing |
| 3 | **Ticket** | The ticket markdown file (path from CLI `--ticket`) | Full content including frontmatter |
| 4 | **PLAN.md** (with-planning only) | `shared/plan/PLAN.md` | Human-readable plan — only present for `with-planning` workflows. |
| 5 | **Prior PUBLIC.md files** | See [Visibility Rules](#visibility-rules) below | Pointers to relevant prior outputs |
| 7 | **Iteration feedback** (iteration > 1) | Reviewer's `PUBLIC.md` for this part + pushback guidance | What the reviewer found lacking. See [Doer Pushback Guidance](#doer-pushback-guidance--iteration-feedback). |
| 7a | **Per-feedback-item instructions** (inner loop) | Single feedback file content + resolution marker instructions + feedback file path | Assembled per feedback item during the inner feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E). |
| 8 | **PUBLIC.md output path** | Computed by provider | Tells the agent where to write its output |
| 9 | **PUBLIC.md writing guidelines** | Static text | Agent work log: decisions + rationale, what was done, codebase discoveries. |
| 10 | **Callback script usage** | Static help text (signal scripts), wrapped in compaction-survival tags | Shows `done completed` for doers |

### Reviewer

Concatenation order (via `AgentInstructionRequest.ExecutionRequest.ReviewerRequest`):

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
| 5 | **Plan format instructions** | Static text — JSON schema for `plan_flow.json` | Must match schema in ref.ap.56azZbk7lAMll0D4Ot2G0.E. |
| 6 | **Reviewer feedback** (iteration > 1) | PLAN_REVIEWER's `PUBLIC.md` | What the plan reviewer found lacking — absent on first iteration |
| 7 | **plan_flow.json output path** | `harness_private/plan_flow.json` (absolute path) | Strict workflow definition — harness-consumed. |
| 8 | **PLAN.md output path** | `shared/plan/PLAN.md` (absolute path) | Human-readable implementation guide (clarified requirements, tradeoffs, architecture constraints, file paths) — fed to all doer sub-parts in `with-planning` workflows. |
| 9 | **PUBLIC.md output path** | `planning/${planner_sub_part}/comm/out/PUBLIC.md` | Planner's rationale and decisions — reviewed by PLAN_REVIEWER |
| 10 | **PUBLIC.md writing guidelines** | Static text | Same as execution agent |
| 11 | **Callback script usage** | Same as execution agent | `callback_shepherd.signal.sh done completed` |

### Plan Reviewer

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | PLAN_REVIEWER role file from `$TICKET_SHEPHERD_AGENTS_DIR` | |
| 2 | **Ticket** | The ticket markdown file | |
| 3 | **plan_flow.json content** | Read from `harness_private/plan_flow.json` | Injected by provider — not in `shared/` |
| 4 | **PLAN.md content** | Read from `shared/plan/PLAN.md` | |
| 5 | **Available agent types & models** | Same as planner receives | Reference for validating planner's `agentType` + `model` assignments |
| 6 | **Planner's PUBLIC.md** | `planning/${planner_sub_part}/comm/out/PUBLIC.md` | Planner's rationale |
| 7 | **Iteration feedback** (iteration > 1) | Plan reviewer's own prior `PUBLIC.md` | What it previously flagged |
| 8 | **PUBLIC.md output path** | Computed by provider | `planning/${plan_review_sub_part}/comm/out/PUBLIC.md` — tells the reviewer where to write its output |
| 9 | **PUBLIC.md writing guidelines** | Static text | Same as execution agent |
| 10 | **Callback script usage** | Same as execution agent | `callback_shepherd.signal.sh done pass` / `needs_iteration` |

---

## Internal Design: Data-Driven Assembly

The four public methods share a single rendering engine. Only the section list (the
"plan") differs per role.

### InstructionSection (sealed class)

Each logical content block is one `InstructionSection` subtype:

| Section type | Description |
|---|---|
| `RoleDefinition` | Full `.md` file for the role from `$TICKET_SHEPHERD_AGENTS_DIR` |
| `PrivateMd` | Self-compaction context from prior session (`${sub_part}/private/PRIVATE.md`). Only present after session rotation (ref.ap.8nwz2AHf503xwq8fKuLcl.E). Skipped silently if file does not exist. |
| `PartContext` | Part `name` and `description` from `current_state.json` |
| `Ticket` | Ticket markdown file content |
| `PlanMd` | `shared/plan/PLAN.md` — always included for `with-planning` workflows; absent for straightforward workflows |
| `PriorPublicMd` | Prior completed PUBLIC.md files per [Visibility Rules](#visibility-rules) |
| `DoerOutputForReview` | Doer's current PUBLIC.md — reviewer only |
| `StructuredFeedbackFormat` | Static structured-feedback format instruction — reviewer only |
| `AddressedFeedback` | `__feedback/addressed/*.md` — reviewer, iteration > 1 |
| `RejectedFeedback` | `__feedback/rejected/*.md` — reviewer, iteration > 1 |
| `RemainingOptionalFeedback` | `__feedback/pending/optional__*.md` — reviewer, iteration > 1 |
| `FeedbackWritingInstructions` | Static instructions for writing feedback files — reviewer only |
| `IterationFeedback` | Reviewer's PUBLIC.md + pushback guidance — doer, iteration > 1 |
| `FeedbackItem` | Single feedback file + resolution instructions (ADDRESSED/REJECTED/SKIPPED) — doer inner-loop only (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) |
| `RoleCatalog` | All role definitions (name + description) — planner only |
| `AvailableAgentTypes` | Supported agent types + models — planner and plan-reviewer |
| `PlanFormatInstructions` | JSON schema for `plan_flow.json` — planner only |
| `PlannerFeedback` | Plan reviewer's PUBLIC.md — planner, iteration > 1 |
| `PlanFlowJsonOutputPath` | Absolute path to `harness_private/plan_flow.json` — planner only |
| `PlanMdOutputPath` | Absolute path to `shared/plan/PLAN.md` — planner only |
| `PlanFlowJsonContent` | Read `harness_private/plan_flow.json` — plan-reviewer only |
| `PlanMdContent` | Read `shared/plan/PLAN.md` — plan-reviewer only |
| `PlannerPublicMd` | Planner's PUBLIC.md — plan-reviewer only |
| `PlanReviewerPriorFeedback` | Plan reviewer's own prior PUBLIC.md — plan-reviewer, iteration > 1 |
| `PublicMdOutputPath` | Computed output path for the agent's PUBLIC.md |
| `WritingGuidelines` | Static PUBLIC.md writing guidance |
| `CallbackHelp` | Compaction-survival callback script usage (role-specific done signal) |

### InstructionPlan per role

```
Doer:         [RoleDefinition, PrivateMd, PartContext, Ticket, PlanMd, PriorPublicMd,
               IterationFeedback, FeedbackItem,
               PublicMdOutputPath, WritingGuidelines, CallbackHelp]

Reviewer:     [RoleDefinition, PrivateMd, PartContext, Ticket, PlanMd, PriorPublicMd,
               DoerOutputForReview, StructuredFeedbackFormat,
               AddressedFeedback, RejectedFeedback, RemainingOptionalFeedback,
               FeedbackWritingInstructions,
               PublicMdOutputPath, WritingGuidelines, CallbackHelp]

Planner:      [RoleDefinition, PrivateMd, Ticket, RoleCatalog, AvailableAgentTypes,
               PlanFormatInstructions, PlannerFeedback,
               PlanFlowJsonOutputPath, PlanMdOutputPath, PublicMdOutputPath,
               WritingGuidelines, CallbackHelp]

PlanReviewer: [RoleDefinition, PrivateMd, Ticket, PlanFlowJsonContent, PlanMdContent,
               AvailableAgentTypes, PlannerPublicMd, PlanReviewerPriorFeedback,
               PublicMdOutputPath, WritingGuidelines, CallbackHelp]
```

### assembleFromPlan

```kotlin
private suspend fun assembleFromPlan(
    plan: List<InstructionSection>,
    request: AgentInstructionRequest,
): Path
```

`assembleInstructions` selects the role-specific plan by dispatching on the sealed type —
exhaustiveness is enforced at compile time, so adding a subtype without a plan entry is a
build error. Then it delegates to `assembleFromPlan`:

```kotlin
suspend fun assembleInstructions(request: AgentInstructionRequest): Path {
    val plan = when (request) {
        is AgentInstructionRequest.ExecutionRequest.DoerRequest      -> doerPlan
        is AgentInstructionRequest.ExecutionRequest.ReviewerRequest  -> reviewerPlan
        is AgentInstructionRequest.PlannerRequest                    -> plannerPlan
        is AgentInstructionRequest.PlanReviewerRequest               -> planReviewerPlan
    }
    return assembleFromPlan(plan, request)
}
```

Each `InstructionSection` smart-casts to the specific subtype it needs — the compiler
guarantees the cast succeeds, so no runtime field-presence checks are needed. Conditional
sections (e.g. `IterationFeedback`, `PlanMd`) still render empty when the optional field
is `null`.

### Benefits

- **DRY**: shared sections (`Ticket`, `WritingGuidelines`, `CallbackHelp`, etc.) have exactly one implementation each.
- **Legible structure**: instruction composition is readable as a data list, not scattered across template methods.
- **Extensible**: new section = one new `InstructionSection` subtype; new role = one new `AgentInstructionRequest` subtype + one plan list.
- **Testable**: each `InstructionSection` can be unit-tested in isolation.
- **Compile-time role safety**: the request type encodes the role — passing reviewer-only fields to a doer request is a build error, not a runtime surprise. No mental tracking of which nullable fields apply to which role.
- **Simplified call site**: `PartExecutor` constructs the right subtype directly — no separate `role` parameter to keep in sync.

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
the inner feedback loop. The doer writes a `## Resolution: ADDRESSED`,
`## Resolution: REJECTED`, or `## Resolution: SKIPPED` (optional items only) marker; the
harness reads it and moves the file accordingly. `SKIPPED` is valid only for `optional__`
items — it signals the doer reviewed the item and chose not to act; the harness moves it
to `addressed/`. Rejections trigger a bounded per-item negotiation with the reviewer (at
most 1 round of disagreement). Full spec:
[`granular-feedback-loop.md`](../plan/granular-feedback-loop.md)
(ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

### Required PUBLIC.md Format on `needs_iteration`

The reviewer's `PUBLIC.md` should include these sections when the verdict is `needs_iteration`:

```markdown
## Verdict: needs_iteration

## Issues
- [ ] <issue-1>: <description> | Severity: must-fix | File(s): <path>
- [ ] <issue-2>: <description> | Severity: should-fix | File(s): <path>

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
  with clear reasoning in the code comments and PUBLIC.md.
- **Do NOT push back for the sake of pushing back.** Only reject feedback when you
  genuinely believe the reviewer is incorrect or missing context.
- **Document your reasoning in PUBLIC.md**: for each reviewer point, state whether you
  accepted or rejected it and why. This helps the reviewer on the next pass understand
  your decisions without re-reviewing unchanged code.
- **Document non-obvious decisions and pitfalls in code comments.** When you reject an
  approach or discover a dead end, leave a comment at the code site so future readers
  understand why.
```

This guidance is wrapped in `<critical_to_keep_through_compaction>` tags alongside the
callback script usage to survive context compaction.

---

## Callback Script Help — Compaction Survival

The callback script usage block is wrapped in `<critical_to_keep_through_compaction>` tags:

```markdown
<critical_to_keep_through_compaction>
## Communicating with the Harness

`callback_shepherd.signal.sh` on your $PATH — fire-and-forget signals to the harness.

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

Health pings arrive wrapped in `<payload_from_shepherd_must_ack>` XML — ACK them the
same way as any other payload (using the command in the `MUST_ACK_BEFORE_PROCEEDING`
attribute) and respond to the ping's question.
</critical_to_keep_through_compaction>
```

The provider inserts the **correct result value** for the agent's role (doer vs. reviewer)
so the agent doesn't have to figure out which values apply to it. All agents (planning and
execution phase) receive the same compaction-survival block above.

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
