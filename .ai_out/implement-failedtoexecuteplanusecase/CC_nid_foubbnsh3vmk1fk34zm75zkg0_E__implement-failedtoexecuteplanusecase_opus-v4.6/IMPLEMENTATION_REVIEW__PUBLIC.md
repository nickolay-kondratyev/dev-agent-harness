# Implementation Review: FailedToExecutePlanUseCase

## Summary

The implementation introduces `FailedToExecutePlanUseCase` to handle blocking plan-execution failures per the spec in `doc/use-case/HealthMonitoring.md` (lines 187-210). The change adds 6 new files across 4 packages: the use case interface + impl, `AllSessionsKiller` interface, `TmuxAllSessionsKiller`, `TicketFailureLearningUseCase` interface + no-op stub, `ProcessExiter` interface + default, and a comprehensive test file.

**Overall assessment: Solid implementation.** The spec is followed faithfully, interfaces are clean, constructor injection is used throughout, tests cover all `PartResult` variants including the `Completed` guard and the non-fatal learning failure path. A few issues are called out below.

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. `TmuxAllSessionsKiller` does not handle `kill-server` failure

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxAllSessionsKiller.kt` (lines 19-23)

`tmux kill-server` returns a non-zero exit code when there is no tmux server running. The current implementation calls `tmuxCommandRunner.run("kill-server")` and logs "all_tmux_sessions_killed" unconditionally, which would be misleading if the command failed. More importantly, if no tmux server is running (a valid state -- e.g., all sessions already exited), this logs success without checking the `ProcessResult`.

**Suggestion:** Check `ProcessResult.exitCode`. Log success on 0, log a warning (non-fatal) on non-zero. The failure case here is non-fatal because the goal is "no sessions remain" which is already true if the server is not running.

```kotlin
override suspend fun killAllSessions() {
    out.info("killing_all_tmux_sessions")
    val result = tmuxCommandRunner.run("kill-server")
    if (result.exitCode == 0) {
        out.info("all_tmux_sessions_killed")
    } else {
        out.warn("tmux_kill_server_non_zero_exit", Val(result.exitCode, ValType.INT))
    }
}
```

### 2. `System.setOut` in tests is not thread-safe

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt` (lines 253-264)

`System.setOut()` is a JVM-global operation. If Kotest runs tests in parallel (which it can), this will cause flaky failures where one test captures another test's output or corrupts the global state. The `finally` block correctly restores, but there is a window during which other tests writing to stdout will have their output swallowed.

**Suggestion:** Instead of intercepting `System.out`, inject a `ConsoleOutput` interface (or similar) into `FailedToExecutePlanUseCaseImpl` that wraps `println`. In tests, substitute a fake that captures the output. This eliminates the thread-safety issue and follows the same pattern used for `ProcessExiter`.

Alternatively, if adding another dependency feels heavy, consider making Kotest run this spec in isolation (single-threaded). But the interface approach is cleaner and consistent with the other testability seams already in this code.

### 3. Test fakes defined in the test file should be extracted to a shared package if reused

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt` (lines 14-46)

`FakeProcessExiter`, `FakeProcessExitException`, `FakeAllSessionsKiller`, and `SpyTicketFailureLearningUseCase` are defined at the top of the test file as public classes. If any other test needs these (likely -- `ProcessExiter` and `AllSessionsKiller` are general infrastructure), they will be duplicated.

**Suggestion:** Either make them `private` (to signal they are local to this test) or move them to a `testFixtures` / shared test support location. Since these are new interfaces, this is the right time to decide.

## Suggestions

### 1. Consider a `ValTypeV2` for exit code

In the `warn` log at line 63-66, the failure message is logged with `ValType.STRING_USER_AGNOSTIC`. The exception message is a reasonable choice here, but consider whether a project-specific `ValTypeV2` entry (e.g., `ValTypeV2.EXCEPTION_MESSAGE`) would be more semantically precise. This is minor and depends on existing conventions in the codebase.

### 2. Missing test: ordering guarantee

The spec says the 4 steps happen **in order**. The tests verify each step happens but do not verify ordering (e.g., that sessions are killed before learning is recorded, or that printing happens before killing). A test verifying that `killAllSessions` is called before `recordFailureLearning` would strengthen confidence in the ordering contract. This could be done with a simple ordered-event collector in the fakes.

### 3. `TmuxAllSessionsKiller` has no unit test

There is no unit test for `TmuxAllSessionsKiller`. While it is simple, a test verifying it calls `tmuxCommandRunner.run("kill-server")` would guard against regressions and is trivial to write with a fake `TmuxCommandRunner`.

## Documentation Updates Needed

None required. The spec at `doc/use-case/HealthMonitoring.md` already documents this use case. The code KDoc references the spec correctly.

## Environment Note

The shell environment on this machine is broken (every bash command fails due to a corrupt profile script at `/Users/nkondrat/vintrin-env/sh/modules/ticket/...`). I was unable to run `./sanity_check.sh`, `./gradlew :app:test`, or any other build/test commands. **Tests have NOT been verified to pass.** This should be confirmed before merging.
