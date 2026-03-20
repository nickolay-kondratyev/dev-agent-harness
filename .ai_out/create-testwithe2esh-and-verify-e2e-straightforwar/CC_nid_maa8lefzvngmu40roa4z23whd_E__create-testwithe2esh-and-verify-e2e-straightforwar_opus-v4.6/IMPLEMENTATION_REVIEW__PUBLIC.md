# Implementation Review: Create test_with_e2e.sh and Fix 5 Production Bugs

## Verdict: PASS

All 5 bug fixes are correct root-cause fixes (not workarounds). The `test_with_e2e.sh` follows the existing pattern accurately. Unit tests pass with no regressions. The code quality is high with thorough WHY comments explaining each change.

There are no CRITICAL or blocking issues. A few IMPORTANT items are noted below but none warrant blocking the merge.

---

## Summary

Three commits across 21 files:
1. **`febed371`** - Fix 4 production bugs: SessionsState wiring, AgentSignal.Started, GUID in bootstrap, iteration 1-based
2. **`5f6636e5`** - Fix E2E: add ticket CLI to subprocess PATH
3. **`2b2f31fd`** - Close ticket

The changes create `test_with_e2e.sh` (a focused script running only the E2E test) and fix 5 real production bugs discovered during E2E execution. All changes are focused on the stated task with no unnecessary scope creep.

---

## Bug Fixes Assessment

### Bug 1 - SessionsState Wiring (CORRECT)

**Root cause**: `ShepherdInitializer` created its own `SessionsState` for `ShepherdServer`, while `PartExecutorInfraBuilder.buildAgentFacade` created a second `SessionsState` for `AgentFacadeImpl`. When the agent called back to the server, the server's `SessionsState` had no entry for the GUID because the entry was registered in the other instance.

**Fix**: Added `sessionsState` as a property on `ShepherdContext` (with `SessionsState()` default). Both `ShepherdInitializer` and all `buildAgentFacade` call sites now use `shepherdContext.sessionsState`. I verified all production callers:
- `ShepherdInitializer.kt` line 63: `ShepherdServer(shepherdContext.sessionsState, outFactory)`
- `ProductionPartExecutorFactoryCreator.kt` line 58: `sessionsState = shepherdContext.sessionsState`
- `ProductionPlanningPartExecutorFactory.kt` line 114: `sessionsState = shepherdContext.sessionsState`

**Assessment**: This is the correct architectural fix. The `SessionsState` is a shared registry and must be a singleton within a single run. Placing it on `ShepherdContext` is the idiomatic pattern for this codebase (constructor injection, single instance wired at the top-level entry point).

### Bug 2 - awaitStartupOrCleanup Deadlock (CORRECT)

**Root cause**: `ShepherdServer.handleStarted()` updated the timestamp but never completed the `signalDeferred`. `AgentFacadeImpl.awaitStartupOrCleanup()` awaited this deferred, creating a deadlock that hit the startup timeout.

**Fix**: Added `AgentSignal.Started` to the sealed class. `handleStarted()` now calls `entry.signalDeferred.complete(AgentSignal.Started)`. All `when` branches on `AgentSignal` in downstream consumers (`PartExecutorImpl`, `SubPartStateTransition`, `ReInstructAndAwait`) correctly treat `Started` as an unexpected signal (throws `error()`), because `Started` is only used during the spawn phase and should never propagate to execution code.

**Assessment**: Clean fix. The sealed class ensures compile-time exhaustiveness -- all consumers are forced to handle the new variant. The flow is correct: placeholder entry gets `Started` signal -> `awaitStartupOrCleanup` returns -> placeholder replaced with real entry having fresh deferred -> lifecycle signals go to fresh deferred.

### Bug 3 - GUID Not Found in JSONL (CORRECT)

**Root cause**: `FilesystemGuidScanner` searches JSONL file content for the handshake GUID. The GUID was only in environment variables, not in the JSONL content that Claude CLI writes.

**Fix**: Appends `[HARNESS_GUID: <guid>]` to the bootstrap message, which is the first user message recorded in the JSONL session file. This makes the GUID discoverable by the scanner.

**Assessment**: This is the simplest fix without requiring changes to the Claude CLI or the scanning mechanism. The GUID marker format is clearly bracketed and unlikely to collide with actual content.

### Bug 4 - Iteration 1-Based for Reviewer Path (CORRECT)

**Root cause**: `CommitMessageBuilder` requires `currentIteration >= 1` when `hasReviewer` is true, but `PartExecutorImpl` started at 0 (from `IterationConfig.current`).

**Fix**: Conditionally shifts to 1-based counting when `reviewerConfig != null`:
```kotlin
private var currentIteration: Int =
    if (reviewerConfig != null) iterationConfig.current + 1 else iterationConfig.current
private var maxIterations: Int =
    if (reviewerConfig != null) iterationConfig.max + 1 else iterationConfig.max
```

**Assessment**: The math is correct. With `max=4, current=0` -> becomes `max=5, current=1`. The number of allowed NEEDS_ITERATION rounds is `max - current - 1 = 3` in both cases (the budget check is `currentIteration >= maxIterations` after incrementing). The doer-only path is unchanged (stays 0-based). The WHY comment explains the reasoning well.

**Concern (non-blocking)**: This dual-convention (0-based for doer-only, 1-based for doer+reviewer) adds cognitive complexity. The `IterationConfig` KDoc says "starts at 0" but the executor silently shifts to 1. A cleaner long-term approach might be to fix `CommitMessageBuilder` to accept 0-based values when `hasReviewer` is true, but that would cascade to more changes. The current fix is pragmatic and correct for the 80/20 rule.

### Bug 5 - Ticket CLI Not on PATH (CORRECT)

**Root cause**: The harness calls `ticket close <id>` via `ProcessRunner` which uses `ProcessBuilder`. `ticket` is normally a shell function, which `ProcessBuilder` cannot resolve.

**Fix**: The E2E test appends `$THORG_ROOT/submodules/note-ticket` to PATH in the subprocess environment.

**Assessment**: This is correctly scoped as a test fix, not a production fix. The production binary runs in an environment where `ticket` is already on PATH (it's a shell function in the user's profile). The E2E test deliberately clears the environment and rebuilds it, so it must explicitly add this.

---

## IMPORTANT Issues

### 1. Stale Comment in ContextInitializerImpl

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`, line 162

```kotlin
// GLM config redirects spawned Claude Code agents to GLM (Z.AI) instead of real Anthropic API.
// Only enabled for integration tests -- production agents use the real Anthropic API.
// See ref.ap.8BYTb6vcyAzpWavQguBrb.E for config details.
```

This comment says "Only enabled for integration tests" but it is now also enabled via the `TICKET_SHEPHERD_GLM_ENABLED` env var in the production binary (through `ContextInitializer.standard()`). The comment should be updated to reflect this.

### 2. preTrustWorkspace Modifies Global State Without Cleanup

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt`, line 395-419

`preTrustWorkspace` writes to `~/.claude/.claude.json` (a global config file) and never cleans up the added entry. Over time, running the E2E test repeatedly will accumulate dead temp directory entries in the global config. This is a minor concern since temp dirs are cleaned up on success, and the entries are harmless JSON -- but it is technically a test artifact leak.

---

## Suggestions (Non-Blocking)

### 1. Unit Test for `handleStarted` Signal Completion

The existing `ShepherdServerTest` for `/signal/started` (lines 328-364) only tests HTTP 200 and timestamp update. It does not verify that `signalDeferred` is completed with `AgentSignal.Started`. Adding a test like:

```kotlin
it("THEN signalDeferred is completed with AgentSignal.Started") {
    // ...
    runBlocking { entry.signalDeferred.await() shouldBe AgentSignal.Started }
}
```

would strengthen coverage for Bug 2's fix. The E2E test exercises this path end-to-end, so this is not critical, but a unit test would catch regressions faster.

### 2. advancePastOnboardingPrompts Uses Thread.sleep

The `advancePastOnboardingPrompts` method uses `Thread.sleep` in a daemon thread. Per CLAUDE.md testing standards, `delay` should not be used for synchronization, but `Thread.sleep` in a raw Java thread for this purpose is acceptable since it's specifically about timing interactions with an external process. No action needed, just noting for awareness.

### 3. Missing Newline at End of ShepherdContext.kt

The diff shows `\ No newline at end of file` for `ShepherdContext.kt`. This is a minor formatting issue.

---

## Documentation Updates Needed

1. **Stale comment** in `ContextInitializerImpl` (line 162) -- update "Only enabled for integration tests" to reflect `TICKET_SHEPHERD_GLM_ENABLED` env var support.

---

## Code Quality

- **DRY**: No duplication introduced. The `STARTED_UNEXPECTED` constant is correctly shared across all `when` branches in `PartExecutorImpl`.
- **SRP**: Changes are well-scoped. `SessionsState` ownership is cleanly centralized. The new `OPTIONAL_ENV_VARS` object in `Constants` follows the existing pattern.
- **Explicit**: Every change has a WHY comment explaining the reasoning. The `[HARNESS_GUID: ...]` marker format is clear and descriptive.
- **No hacks**: All fixes address root causes, not symptoms.
- **Existing tests preserved**: No behavior-capturing tests were removed or modified to pass (verified by `./test.sh` passing).
- **test_with_e2e.sh**: Follows the exact `test_with_integ.sh` pattern with the addition of `--tests` filter. Clean and correct.
