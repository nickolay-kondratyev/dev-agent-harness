# Implementation Iteration: Review Feedback for FailedToExecutePlanUseCase

## Changes Made

### 1. FIX: TmuxAllSessionsKiller handles non-zero exit from `tmux kill-server`
**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxAllSessionsKiller.kt`

Now checks `ProcessResult.exitCode`. Logs INFO on success (`all_tmux_sessions_killed`) and logs INFO (not WARN -- this is expected) when no server was running (`tmux_server_not_running_nothing_to_kill`). Used `ValType.COUNT` for the exit code since there is no `ValType.INT` in the asgard library.

### 2. FIX: Replaced `System.setOut` with injectable `ConsoleOutput` interface
**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ConsoleOutput.kt` (NEW)
**File:** `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCase.kt` (MODIFIED)

Created `ConsoleOutput` interface with `printlnRed(message)` and `DefaultConsoleOutput` implementation that applies ANSI red codes. `FailedToExecutePlanUseCaseImpl` now takes `ConsoleOutput` as a constructor parameter instead of directly calling `println`. ANSI constants moved into `DefaultConsoleOutput`.

Tests now use `FakeConsoleOutput` that captures messages in a list -- no more `System.setOut` hack.

### 3. FIX: Test fakes marked `internal`
**File:** `app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt`

`FakeProcessExitException`, `FakeProcessExiter`, `FakeConsoleOutput`, `FakeAllSessionsKiller`, and `SpyTicketFailureLearningUseCase` are now `internal`.

### 4. ADD: Ordering test
Added test "THEN steps execute in order: print -> kill -> learning -> exit" using an `OrderTracker` with a shared event list. Each fake records its event name. Test asserts exact ordering.

### 5. TmuxAllSessionsKiller unit test -- skipped (per plan)
Added KDoc comment noting it is tested via integration tests. The class is a thin wrapper around `TmuxCommandRunner` which uses `ProcessBuilder` internally -- not worth the refactoring cost to make it unit-testable.

## Test Results

All tests pass: `./gradlew :app:test` -- BUILD SUCCESSFUL.
