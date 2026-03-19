# Clarification: FailedToExecutePlanUseCase

## Requirements — Clear from spec (no ambiguities)

The spec at `doc/use-case/HealthMonitoring.md` lines 187-210 is unambiguous:
1. Print failure reason in red
2. Kill all TMUX sessions
3. Record failure learning (best-effort, non-fatal)
4. Exit with non-zero code

## Design Decisions Made

### Session Killing Strategy
- Create `AllSessionsKiller` interface for DIP/testability
- `TmuxAllSessionsKiller` implementation uses `tmux kill-server` via `TmuxCommandRunner`
- This kills ALL tmux sessions on the machine — acceptable because:
  - The shepherd manages its own tmux sessions exclusively
  - On failure, all sessions should be terminated regardless
  - `kill-server` is deterministic and simple

### ProcessExiter for Testability
- `ProcessExiter` interface with `fun exit(code: Int): Nothing`
- Default impl calls `kotlin.system.exitProcess`
- Tests use a fake that throws an exception to verify exit was called

### TicketFailureLearningUseCase
- Create interface + no-op stub (real implementation is a separate ticket)
- Interface signature simplified to `suspend fun recordFailureLearning(partResult: PartResult)`
  - V1 doesn't need the full `FailureLearningRequest` since we don't have the full context yet
  - Will evolve when the real implementation ticket is worked on

## No ambiguities requiring human input. Proceeding to implementation.
