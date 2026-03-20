# Implementation: Create test_with_e2e.sh and verify E2E test passes

## Summary

Created `test_with_e2e.sh` and ran the E2E straightforward workflow integration test against
real GLM-backed agents. The test initially failed due to 5 production bugs discovered during
E2E execution. All 5 bugs were fixed and the E2E test now passes.

## What was done

### 1. Created `test_with_e2e.sh`
Following the exact pattern of `test_with_integ.sh`. Runs only the E2E test class with
proper asgard dependency prep and binary build.

### 2. Fixed 5 production bugs discovered by E2E test

**Bug 1 - SessionsState wiring (signal_unknown_handshake_guid)**
- Root cause: `SessionsState` was created as two separate instances -- one for `ShepherdServer`,
  one for `AgentFacadeImpl`. When the agent sent callbacks, the server's SessionsState had no
  entry for the GUID.
- Fix: Added `sessionsState` property to `ShepherdContext` so a single instance is shared.
  Updated `ShepherdInitializer` and `ProductionPlanningPartExecutorFactory` to use it.

**Bug 2 - awaitStartupOrCleanup deadlock (3-minute timeout)**
- Root cause: `handleStarted` in `ShepherdServer` updated timestamps but never completed
  the `signalDeferred`. `awaitStartupOrCleanup` waited forever for this deferred.
- Fix: Added `AgentSignal.Started` to the sealed class. Updated `handleStarted` to call
  `entry.signalDeferred.complete(AgentSignal.Started)`. Added exhaustive `when` branches
  in all signal consumers (PartExecutorImpl, SubPartStateTransition, ReInstructAndAwait).

**Bug 3 - GUID not found in JSONL files (45-second timeout)**
- Root cause: `FilesystemGuidScanner` searches JSONL file content for the handshake GUID.
  The GUID was only in environment variables, not in the JSONL content.
- Fix: Modified `ClaudeCodeAdapter.buildStartCommand` to append
  `[HARNESS_GUID: <guid>]` to the bootstrap message so it appears in JSONL content.

**Bug 4 - Iteration 1-based for reviewer path (currentIteration=0 validation)**
- Root cause: `CommitMessageBuilder` requires `currentIteration >= 1` when `hasReviewer` is
  true, but `PartExecutorImpl` started iteration at 0.
- Fix: Changed `PartExecutorImpl` to use 1-based iteration counting when `reviewerConfig`
  is present (`currentIteration = iterationConfig.current + 1`,
  `maxIterations = iterationConfig.max + 1`). This preserves the same number of allowed
  NEEDS_ITERATION feedback rounds.

**Bug 5 - ticket CLI not on subprocess PATH**
- Root cause: The harness calls `ticket close <id>` via `ProcessRunner` after successful
  workflow completion. The `ticket` CLI is a standalone script normally exposed via a shell
  function, but `ProcessBuilder` cannot resolve shell functions.
- Fix: Added `$THORG_ROOT/submodules/note-ticket` to the subprocess PATH in the E2E test.

### 3. E2E test verified passing

All 3 assertions pass:
- Process exits with code 0
- `.ai_out/` directory created
- Feature branch created (not on initial branch)

### 4. Unit tests verified -- no regressions

`./gradlew :app:test` passes after all changes.

## Files modified

### New files
- `test_with_e2e.sh` -- focused E2E test runner script

### Production code fixes
- `app/src/main/kotlin/.../initializer/data/ShepherdContext.kt` -- added `sessionsState` property
- `app/src/main/kotlin/.../initializer/ShepherdInitializer.kt` -- use shared SessionsState
- `app/src/main/kotlin/.../planning/ProductionPlanningPartExecutorFactory.kt` -- use shared SessionsState
- `app/src/main/kotlin/.../executor/ProductionPartExecutorFactoryCreator.kt` -- use shared SessionsState
- `app/src/main/kotlin/.../executor/PartExecutorInfraBuilder.kt` -- sessionsState parameter
- `app/src/main/kotlin/.../agent/facade/AgentSignal.kt` -- added Started variant
- `app/src/main/kotlin/.../server/ShepherdServer.kt` -- complete signalDeferred in handleStarted
- `app/src/main/kotlin/.../executor/PartExecutorImpl.kt` -- Started branches + 1-based iteration
- `app/src/main/kotlin/.../state/SubPartStateTransition.kt` -- Started branch
- `app/src/main/kotlin/.../reinstructandawait/ReInstructAndAwait.kt` -- Started branch
- `app/src/main/kotlin/.../agent/adapter/ClaudeCodeAdapter.kt` -- GUID in bootstrap message
- `app/src/main/kotlin/.../initializer/ContextInitializer.kt` -- GLM env var support
- `app/src/main/kotlin/.../Constants.kt` -- OPTIONAL_ENV_VARS

### Test code fixes
- `app/src/test/kotlin/.../e2e/StraightforwardWorkflowE2EIntegTest.kt` -- ticket PATH, diagnostic improvements

## Notes

- E2E test takes ~12 minutes due to the 5-minute health check interval in `HealthAwareAwaitLoop`.
  Each signal (doer Done, reviewer Done) must wait up to 5 minutes for the health loop to check
  the deferred. This is a known production behavior and not a bug.
- The `test_with_e2e.sh` script uses a 15-minute Gradle timeout which is sufficient.
