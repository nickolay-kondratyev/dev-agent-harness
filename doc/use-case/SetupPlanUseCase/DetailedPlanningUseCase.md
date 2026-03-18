## DetailedPlanningUseCase / ap.cJhuVZTkwfrWUzTmaMbR3.E

Owns the full planning lifecycle for `with-planning` workflows. Creates a planning executor,
runs it, converts the approved plan to execution parts, and returns `List<Part>`.

```kotlin
suspend fun execute(): List<Part>
```

The caller (`SetupPlanUseCase` ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) receives execution-ready parts
with no knowledge of the two-phase protocol (run executor → convert plan).

---

### What It Does

1. Creates a `PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E, configured with reviewer)
   for the PLANNER↔PLAN_REVIEWER iteration loop.
2. Runs `planningExecutor.execute()` → `PartResult`.
3. Handles `PartResult`:
   - `Completed` → proceeds to plan conversion (step 4).
   - Any failure (`FailedWorkflow`, `FailedToConverge`, `AgentCrashed`) →
     delegates to `FailedToExecutePlanUseCase(partResult)` (red error, kills all sessions, exits non-zero).
     When they happen, the human is the right handler — no special recovery logic.
4. Kills TMUX sessions for the planning part (`removeAllForPart`).
5. Calls `convertPlanToExecutionParts()` → `List<Part>`.
   - On `PlanConversionException`: logs WARN, **restarts the planning loop** with
     validation errors injected as planner context. Counts against the planning
     iteration budget. If budget exhausted, exits non-zero via `FailedToExecutePlanUseCase`.
6. Returns the execution parts.

---

### Planning Executor Setup

Creates a `PartExecutorImpl` (with reviewer) configured for the planning phase:

| Aspect | Value |
|--------|-------|
| Doer role | `PLANNER` |
| Reviewer role | `PLAN_REVIEWER` |
| `ContextForAgentProvider` | Shared provider — executor calls `assembleInstructions()` with `PlannerRequest` / `PlanReviewerRequest` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) |
| Iteration semantics | Same as execution parts — reviewer signals `pass` or `needs_iteration` |

### Planning Instruction Assembly

The planning executor uses `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) directly —
the same provider used by execution executors. The executor calls `assembleInstructions(request)`
with the appropriate sealed subtype based on the sub-part role:

| Sub-Part | Request Subtype | Content |
|----------|----------------|---------|
| PLANNER (doer) | `AgentInstructionRequest.PlannerRequest` | Ticket + role catalog + available agent types & models (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E) + plan format instructions + reviewer feedback (on iteration) |
| PLAN_REVIEWER (reviewer) | `AgentInstructionRequest.PlanReviewerRequest` | Ticket + `plan_flow.json` from `harness_private/` + `PLAN.md` from `shared/plan/` + review criteria |

---

### convertPlanToExecutionParts

Called internally **after** the planning executor completes successfully
(`PartResult.Completed`). Transforms the approved plan into executable parts.

```
1. Read plan_flow.json from harness_private/plan_flow.json
2. Validate plan_flow.json against the parts/sub-parts schema (ref.ap.56azZbk7lAMll0D4Ot2G0.E):
   a. Valid JSON conforming to schema (required fields, types, phase = "execution")
   b. At least one execution part exists
   c. Every agentType is a supported type (V1: ClaudeCode)
   d. Every model is valid for the given agentType
   e. Every role value matches an existing .md file in $TICKET_SHEPHERD_AGENTS_DIR (catches
      non-existent role assignments before execution starts)
3. Append execution parts (phase: "execution") to the existing parts array in
   the in-memory CurrentState (planning part at index 0 is preserved); flush to
   current_state.json
4. Delete plan_flow.json (in-memory CurrentState is the single source of truth)
5. Return List<Part> — the execution parts extracted from in-memory CurrentState
```

If `plan_flow.json` is malformed or fails schema validation, `convertPlanToExecutionParts` throws
a `PlanConversionException` (extends `AsgardBaseException`). `DetailedPlanningUseCase` catches
`PlanConversionException`, logs a **WARN** with the validation errors, and **restarts the
planning loop** — injecting the validation errors as context for the planner on the next
attempt. This counts against the planning iteration budget. If the budget is exhausted,
exits non-zero via `FailedToExecutePlanUseCase` (red error). This is the **single validation point** —
harness-side validation is the source of truth, eliminating drift between what agents
validate and what the harness validates.

---

### Planning Sub-Parts Follow Execution Semantics

The planning phase uses the exact same `PartExecutorImpl` as execution parts.
This means:

- Same `CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) callback bridge
- Same TMUX session lifecycle (sessions kept alive across iterations)
- Same iteration budget enforcement (`iteration.max`)
- Same health monitoring (timeout → ping → crash detection)
- Same `PartResult` outcomes (`Completed`, `FailedWorkflow`, `FailedToConverge`, `AgentCrashed`)

The only difference is the instruction content (the executor calls `ContextForAgentProvider`'s
planner/plan-reviewer methods instead of the execution doer/reviewer methods).
