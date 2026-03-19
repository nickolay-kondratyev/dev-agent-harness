# Implementation: FailedToExecutePlanUseCase

## What Was Done

Implemented the `FailedToExecutePlanUseCase` — the use case that handles blocking plan-execution failures by printing the error in red, killing all TMUX sessions, recording failure learning (best-effort), and exiting the process.

## Files Created

### Production Code

1. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCase.kt`**
   - `FailedToExecutePlanUseCase` interface with `suspend fun handleFailure(failedResult: PartResult): Nothing`
   - `FailedToExecutePlanUseCaseImpl` — default implementation with 4-step failure handling

2. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AllSessionsKiller.kt`**
   - `AllSessionsKiller` interface with `suspend fun killAllSessions()`

3. **`app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxAllSessionsKiller.kt`**
   - `TmuxAllSessionsKiller` — runs `tmux kill-server` via `TmuxCommandRunner`

4. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCase.kt`**
   - `TicketFailureLearningUseCase` interface
   - `NoOpTicketFailureLearningUseCase` — V1 stub (no-op)

5. **`app/src/main/kotlin/com/glassthought/shepherd/core/infra/ProcessExiter.kt`**
   - `ProcessExiter` interface for testable process exit
   - `DefaultProcessExiter` — calls `kotlin.system.exitProcess`

### Test Code

6. **`app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt`**
   - 15 tests covering all PartResult variants, session killing, exit code, ANSI output, and learning failure resilience
   - Test fakes: `FakeProcessExiter`, `FakeAllSessionsKiller`, `SpyTicketFailureLearningUseCase`

## Behavior Summary

The use case performs 4 steps in order:
1. Prints failure reason in RED (ANSI codes) — exhaustive `when` on `PartResult` (no `else`)
2. Kills all TMUX sessions via `AllSessionsKiller`
3. Records failure learning (best-effort — exception caught, logged as WARN, continues)
4. Exits process with code 1 via `ProcessExiter`

`PartResult.Completed` reaching this use case throws `IllegalArgumentException`.

## Test Results

15 tests, 0 failures, 0 skipped.
