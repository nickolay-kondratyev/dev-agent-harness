## DetailedPlanningUseCase / ap.cJhuVZTkwfrWUzTmaMbR3.E

Creates the planning executor and plan-conversion function for `with-planning` workflows.
Returns a `SetupPlanResult.NeedsPlanning` (ref.ap.evYmpQfliHCHUTdK2QRgS.E) containing
a `DoerReviewerPartExecutor` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) for the PLANNER↔PLAN_REVIEWER
iteration loop, plus a `convertPlanToExecutionParts` function that transforms the approved
plan into executable parts.

---

### What It Returns

```kotlin
SetupPlanResult.NeedsPlanning(
    planningExecutor = doerReviewerPartExecutor,    // PLANNER↔PLAN_REVIEWER loop
    convertPlanToExecutionParts = ::convertPlan,     // plan.json → List<Part>
)
```

The use case does **not** run the planning loop itself — it creates the executor and hands it
back to `TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E), which runs it via `execute()`.

---

### Planning Executor Setup

Creates a `DoerReviewerPartExecutor` configured for the planning phase:

| Aspect | Value |
|--------|-------|
| Doer role | `PLANNER` |
| Reviewer role | `PLAN_REVIEWER` |
| `SubPartInstructionProvider` | Planning-specific implementation (see below) |
| Iteration semantics | Same as execution parts — reviewer signals `pass` or `needs_iteration` |

### Planning SubPartInstructionProvider

A planning-specific implementation of `SubPartInstructionProvider`
(ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) that wraps `ContextForAgentProvider`
(ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) planner/plan-reviewer methods:

| Method | Delegates to | Content |
|--------|-------------|---------|
| `assembleDoerInstructions()` | `ContextForAgentProvider` planner assembly | Ticket + role catalog + plan format instructions + reviewer feedback (on iteration) |
| `assembleReviewerInstructions()` | `ContextForAgentProvider` plan-reviewer assembly | Ticket + `plan.json` from `harness_private/` + review criteria |

---

### convertPlanToExecutionParts

Called by `TicketShepherd` **after** the planning executor completes successfully
(`PartResult.Completed`). Transforms the approved plan into executable parts.

```
1. Read plan.json from harness_private/planning/
2. Validate plan.json against the parts/sub-parts schema (ref.ap.56azZbk7lAMll0D4Ot2G0.E)
3. Convert plan.json → current_state.json (write to harness_private/)
4. Delete plan.json (current_state.json is now the single source of truth)
5. Return List<Part> — the execution parts extracted from current_state.json
```

If `plan.json` is malformed or fails schema validation, this function throws — the caller
(`TicketShepherd`) handles the error.

---

### Planning Sub-Parts Follow Execution Semantics

The planning phase uses the exact same `DoerReviewerPartExecutor` as execution parts.
This means:

- Same `CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) callback bridge
- Same TMUX session lifecycle (sessions kept alive across iterations)
- Same iteration budget enforcement (`iteration.max`)
- Same health monitoring (timeout → ping → crash detection)
- Same `PartResult` outcomes (`Completed`, `FailedWorkflow`, `FailedToConverge`, `AgentCrashed`)

The only difference is the instruction content (assembled by the planning-specific
`SubPartInstructionProvider`).
