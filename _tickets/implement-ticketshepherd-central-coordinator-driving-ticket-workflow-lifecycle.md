---
closed_iso: 2026-03-19T20:35:47Z
id: nid_fqzi45z9xja51yy4yk3m3mkwh_E
title: "Implement TicketShepherd — central coordinator driving ticket workflow lifecycle"
status: closed
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_89a09ady4m86saemw4rul5b4s_E, nid_5z93biuqub3mhcejfpofjmj39_E, nid_foubbnsh3vmk1fk34zm75zkg0_E, nid_eq78xsmd72qtrycecxb7ltqp7_E, nid_m3cm8xizw5qhu1cu3454rca79_E, nid_fwf09ycnd4d8wdoqd1atuohgb_E, nid_g3e2bkvqepf2wzipadayt9in8_E]
links: []
created_iso: 2026-03-19T00:40:31Z
status_updated_iso: 2026-03-19T20:35:47Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, ticket-shepherd, core]
---

## Context

Spec: `doc/core/TicketShepherd.md` (ref.ap.P3po8Obvcjw4IXsSUSU91.E)

`TicketShepherd` is the central coordinator that drives a ticket through its entire workflow lifecycle.
It sets up the plan, creates executors for each part, runs them in sequence, and handles the results.

## What to Implement

### 1. TicketShepherd class
```kotlin
class TicketShepherd(
    private val agentFacade: AgentFacade,
    private val setupPlanUseCase: SetupPlanUseCase,
    private val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
    private val interruptHandler: InterruptHandler,  // from interrupt protocol ticket
    private val currentState: CurrentState,
    private val gitCommitStrategy: GitCommitStrategy,
    val originatingBranch: String,
    val tryNumber: Int,
    private val out: Out,
) {
    private var activeExecutor: PartExecutor? = null

    suspend fun run() { ... }
}
```

### 2. Main Loop (`run()` method)
1. Call `SetupPlanUseCase.setup()` -> `List<Part>`
2. For each Part:
   a. Create `PartExecutorImpl`:
      - 2 sub-parts -> `PartExecutorImpl(reviewerConfig = reviewerSubPart)`
      - 1 sub-part -> `PartExecutorImpl(reviewerConfig = null)`
   b. Set `activeExecutor = executor`
   c. Call `executor.execute()` -> `PartResult`
   d. Handle `PartResult`:
      - `Completed` -> kill TMUX sessions for part (`agentFacade.removeAllForPart`), move to next part
      - `FailedWorkflow` -> delegate to `FailedToExecutePlanUseCase`
      - `FailedToConverge` -> delegate to `FailedToExecutePlanUseCase`
      - `AgentCrashed` -> delegate to `FailedToExecutePlanUseCase`
   e. Set `activeExecutor = null` between parts
3. On all parts completed (workflow success):
   a. **Final commit** — `git add -A && git commit` to capture remaining state (e.g., final CurrentState flush). Skip if working tree is clean.
   b. **Update ticket status** — set ticket `status` to `done` in YAML frontmatter.
   c. **Kill all TMUX sessions** — defensive cleanup via `AgentFacade`.
   d. **Print success message in green** — e.g., `"Workflow completed successfully for ticket {TICKET_ID}."`
   e. **Exit code 0**
   f. Does NOT push the branch.

### 3. Fields
| Field | Type | Purpose |
|-------|------|--------|
| `activeExecutor` | `PartExecutor?` | Currently running executor. Single reference for cancellation. `null` between parts. |
| `originatingBranch` | `String` | Branch from which try branch was created. Set by TicketShepherdCreator. |
| `tryNumber` | `Int` | Try number for this run. Set by TicketShepherdCreator. |

### 4. What TicketShepherd Does NOT Do
- Does NOT interpret `/callback-shepherd/signal/done` results directly.
- Does NOT assemble agent instructions.
- Does NOT run or orchestrate the planning phase.
- Does NOT drive the doer/reviewer loop directly.

### 5. Dependencies
- `AgentFacade` (nid_m7oounvwb31ra53ivu7btoj5v_E) — for session management.
- `SetupPlanUseCase` (nid_89a09ady4m86saemw4rul5b4s_E) — to get execution parts.
- `PartExecutorImpl` (nid_5z93biuqub3mhcejfpofjmj39_E) — to execute each part.
- `FailedToExecutePlanUseCase` (nid_foubbnsh3vmk1fk34zm75zkg0_E) — for failure handling.
- `InterruptHandler` (nid_eq78xsmd72qtrycecxb7ltqp7_E) — for Ctrl+C protocol.
- `TicketShepherdCreator` (nid_itogi6ji82dbhb0k3zzt6v8qp_E) — wires and creates TicketShepherd.
- `CurrentState` (nid_m3cm8xizw5qhu1cu3454rca79_E) — in-memory state.
- `GitCommitStrategy` (nid_fwf09ycnd4d8wdoqd1atuohgb_E) — for final commit.

### 6. Testing
- Unit tests using `FakeAgentFacade` (nid_g3e2bkvqepf2wzipadayt9in8_E).
- Test happy path: all parts complete -> final commit, status update, cleanup, success.
- Test single part failure: FailedWorkflow -> delegates to FailedToExecutePlanUseCase.
- Test FailedToConverge -> delegates to FailedToExecutePlanUseCase.
- Test AgentCrashed -> delegates to FailedToExecutePlanUseCase.
- Test activeExecutor is set during execution and null between parts.
- Test final commit is skipped when working tree is clean.
- Test part with 2 sub-parts creates PartExecutorImpl with reviewerConfig.
- Test part with 1 sub-part creates PartExecutorImpl without reviewerConfig.

