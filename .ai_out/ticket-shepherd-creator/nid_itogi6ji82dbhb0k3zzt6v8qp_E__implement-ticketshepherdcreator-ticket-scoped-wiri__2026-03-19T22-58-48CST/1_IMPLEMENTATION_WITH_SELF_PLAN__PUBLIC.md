# TicketShepherdCreator Implementation

## What Was Done

Implemented the full `TicketShepherdCreator` interface and `TicketShepherdCreatorImpl` in the
`com.glassthought.shepherd.core.creator` package. This is a ticket-scoped wiring factory that
creates a fully wired `TicketShepherd` for a single run.

### New Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`
  - `TicketShepherdCreator` interface with `suspend fun create(shepherdContext, ticketPath, workflowName): TicketShepherd`
  - `TicketShepherdCreatorImpl` — production implementation with constructor-injected shared deps
  - Factory interfaces: `SetupPlanUseCaseFactory`, `AllSessionsKillerFactory`
  - Internal result types: `GitSetupResult`, `StateSetupResult`

- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`
  - 13 test cases covering all required scenarios

### Modified Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherdCreator.kt`
  - Marked as **SUPERSEDED** by the new implementation
  - Removed `@AnchorPoint` annotation (anchor now lives on the new implementation)
  - Kept for backward compatibility with existing tests

## Sequential Steps in `create()`

1. **Parse workflow** — `WorkflowParser.parse(workflowName, workingDir)`
2. **Parse ticket** — `TicketParser.parse(ticketPath)`
3. **Validate frontmatter** — fail hard if `id`, `title`, or `status` missing/empty
4. **Validate status** — must be `in_progress`
5. **Validate working tree** — `WorkingTreeValidator.validate()`
6. **Record originating branch** — `GitBranchManager.getCurrentBranch()`
7. **Resolve try-N** — `TryNResolver.resolve(ticketData)`
8. **Create feature branch** — `GitBranchManager.createAndCheckout(branchName)`
9. **Set up .ai_out/** — `AiOutputStructure.ensureStructure(parts)`
10. **Create CurrentState** — `CurrentStateInitializer.createInitialState()`, flush to disk
11. **Wire TicketShepherd** — construct InterruptHandler, SetupPlanUseCase, FailedToExecutePlanUseCase, TicketShepherdDeps

## Test Coverage

| Test | What It Verifies |
|------|-----------------|
| Blank id | IllegalStateException mentioning "id" |
| Null status | IllegalStateException mentioning "status" |
| Wrong status (open) | IllegalStateException mentioning "in_progress" and "open" |
| Dirty working tree | IllegalStateException propagated |
| Clean working tree | Validation called, proceeds |
| Originating branch | Correctly recorded from getCurrentBranch() |
| Try-N resolution | Returns correct number, resolver called with ticket data |
| Feature branch name | Created with BranchNameBuilder format |
| Straightforward workflow | TicketShepherd returned successfully |
| With-planning workflow | TicketShepherd returned successfully |
| .ai_out/ structure | Directories created on disk |
| CurrentState flush | current_state.json written to disk |
| Workflow name forwarding | Parser receives correct name |

## Design Decisions

1. **New package `core.creator`** — avoids namespace collision with the existing partial impl in `core`.
2. **Factory interfaces** for `SetupPlanUseCase` and `AllSessionsKiller` — enables testability without constructing real infrastructure.
3. **Removed AgentFacadeImpl/ContextForAgentProvider construction** from this scope — these are needed by `PartExecutorFactory` which is injected externally. Full internal wiring deferred to when PartExecutorFactory production wiring is implemented.
4. **`NoOpTicketFailureLearningUseCase`** used as default — failure learning is best-effort and V1 uses a no-op.
5. **Anchor point `ap.cJbeC4udcM3J8UFoWXfGh.E`** moved to the new implementation.

## Not Yet Wired (Future Work)

- `PartExecutorFactory` production wiring (requires AgentFacadeImpl + ContextForAgentProvider)
- `FinalCommitUseCase` production implementation
- `TicketStatusUpdater` production implementation
- `TicketFailureLearningUseCaseImpl` (currently using NoOp)
