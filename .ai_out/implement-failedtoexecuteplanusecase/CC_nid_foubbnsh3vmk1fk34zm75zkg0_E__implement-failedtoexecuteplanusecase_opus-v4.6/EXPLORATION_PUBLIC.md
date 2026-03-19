# Exploration: FailedToExecutePlanUseCase

## Key Findings

### 1. PartResult — EXISTS
- **File**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/PartResult.kt`
- Sealed class with 4 variants: `Completed`, `FailedWorkflow(reason)`, `FailedToConverge(summary)`, `AgentCrashed(details)`

### 2. TicketFailureLearningUseCase — NOT YET IMPLEMENTED
- Spec exists at `doc/use-case/TicketFailureLearningUseCase.md`
- No Kotlin code exists yet. Need to create **interface only** + no-op stub per ticket instructions.
- Complex request type (`FailureLearningRequest`) with ticketPath, tryNumber, branchName, etc.
- For V1: create simple interface + no-op. The real implementation will be a separate ticket.

### 3. Session Killing
- `TmuxSessionManager.killSession(session: TmuxSession)` — kills individual sessions
- `AgentFacade.killSession(handle: SpawnedAgentHandle)` — kills by handle
- `TmuxCommandRunner` can run arbitrary tmux commands
- **No existing "kill all" method** — need to create one
- Simplest: use `tmux kill-server` via TmuxCommandRunner (kills all tmux sessions + server)
- Better for testability: inject an `AllSessionsKiller` interface

### 4. Console Output / ANSI Colors
- No existing ANSI color utilities in codebase
- `println()` is used for user-facing console output (allowed per CLAUDE.md)
- Need to use ANSI escape codes directly: `\u001b[31m` (red) + `\u001b[0m` (reset)

### 5. Process Exit
- No `exitProcess` or `ProcessExiter` exists in codebase yet
- Need a `ProcessExiter` interface for testability (can't test `exitProcess` directly)

### 6. Use Case Patterns
- Constructor injection with OutFactory
- `private val out = outFactory.getOutForClass(MyClass::class)`
- Package: `com.glassthought.shepherd.usecase.healthmonitoring` (new)
- No existing use case implementations in the codebase yet (only specs)

### 7. Out/OutFactory Logging
- `out.info("snake_case_message", Val(value, ValType.SPECIFIC_TYPE))`
- `out.warn("snake_case_message", Val(value, ValType.SPECIFIC_TYPE))`
- All methods are `suspend`

## Design Decisions

1. **Interface**: `FailedToExecutePlanUseCase` with `suspend fun handleFailure(failedResult: PartResult): Nothing`
2. **Dependencies** (constructor-injected):
   - `OutFactory` — logging
   - `AllSessionsKiller` — kills all tmux sessions
   - `TicketFailureLearningUseCase` — best-effort failure learning (no-op stub for now)
   - `ProcessExiter` — testable exit wrapper
3. **Console red output**: Simple ANSI escape codes
4. **TicketFailureLearningUseCase**: Interface-only + `NoOpTicketFailureLearningUseCase` stub
