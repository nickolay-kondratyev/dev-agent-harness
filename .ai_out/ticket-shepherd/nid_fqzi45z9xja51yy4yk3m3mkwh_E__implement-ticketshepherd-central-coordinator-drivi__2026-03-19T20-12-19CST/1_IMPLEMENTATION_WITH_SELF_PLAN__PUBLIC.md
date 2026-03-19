# Implementation: TicketShepherd Central Coordinator

## What Was Done

Implemented `TicketShepherd` — the central coordinator that drives a ticket through its entire workflow lifecycle. Also created supporting interfaces and updated `ConsoleOutput`.

### New Files

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt` | Central coordinator + `TicketShepherdDeps` bundle |
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactory.kt` | Factory interface for testable executor creation |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCase.kt` | Interface for final git commit after workflow success |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/ticketstatus/TicketStatusUpdater.kt` | Interface for updating ticket status to done |
| `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt` | Comprehensive BDD tests (19 test cases) |

### Modified Files

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ConsoleOutput.kt` | Changed from `fun interface` to `interface`, added `printlnGreen` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` | Added `PART_NAME` val type |
| `app/src/test/.../FailedToExecutePlanUseCaseImplTest.kt` | Updated `FakeConsoleOutput` for `printlnGreen` |
| `app/src/test/.../TicketShepherdCreatorTest.kt` | Updated `FakeConsoleOutput` for `printlnGreen` |
| `app/src/test/.../InterruptHandlerTest.kt` | Updated `FakeConsoleOutput` for `printlnGreen` |

## Design Decisions

1. **`TicketShepherdDeps` bundle** — Groups 10 collaborators into a data class to satisfy detekt's `LongParameterList` rule (threshold=8). `currentState`, `originatingBranch`, and `tryNumber` remain direct constructor params since they are state/identity, not collaborators.

2. **`PartExecutorFactory` fun interface** — Decouples TicketShepherd from PartExecutorImpl construction details. Test code substitutes a fake factory returning controlled executors.

3. **`run()` returns `Nothing`** — Both success (exit(0)) and failure (handleFailure which exits) terminate the process, so the function signature is `: Nothing`.

4. **No `else` branch on `when(result)`** — Uses sealed class exhaustiveness checking per project standards.

5. **Test fake naming** — Prefixed with `Ts` (TicketShepherd) to avoid Kotlin's same-package redeclaration error with private top-level classes in `TicketShepherdCreatorTest.kt`.

## Test Coverage

- Happy path: 2 parts complete -> final commit, status update, cleanup, green message, exit(0)
- FailedWorkflow -> delegates to FailedToExecutePlanUseCase, skips success path
- FailedToConverge -> delegates to FailedToExecutePlanUseCase
- AgentCrashed -> delegates to FailedToExecutePlanUseCase
- Empty plan -> still performs final commit, exits 0
- activeExecutor tracking: non-null during execution, null after
- Success path ordering: finalCommit -> markDone -> killSessions -> printGreen -> exit
- originatingBranch and tryNumber accessibility
