# Implementation Review: E2E Integration Test for Straightforward Workflow

## Summary

The change adds a black-box end-to-end integration test (`StraightforwardWorkflowE2EIntegTest.kt`) that runs the actual `app` binary as a subprocess against a temporary git repo with a simple ticket. It also updates `test_with_integ.sh` to run `installDist` before tests.

**Overall assessment**: The implementation is well-structured and follows the approved plan with reviewer adjustments. The approach (subprocess via ProcessBuilder, GLM via env inheritance, delta-based tmux cleanup) is sound. There is one correctness issue with `--iteration-max 1` being a no-op, and several minor issues.

**Unit tests pass**: `./gradlew :app:test` and `./sanity_check.sh` both pass.

---

## MUST FIX

### 1. `--iteration-max 1` is a no-op -- test claim is misleading

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt` (line 178)

**Issue**: The test passes `--iteration-max 1` to the binary, but this value is NOT consumed downstream. In `ShepherdInitializer.kt` (lines 68-70), there is an explicit comment:

```kotlin
// DEFERRED: thread cliParams.iterationMax to TicketShepherdCreator.create() when its
//  signature supports it. Currently iterationMax is parsed from CLI but not yet
//  consumed downstream. See ref.ap.mFo35x06vJbjMQ8m7Lh4Z.E.
```

The workflow JSON (`config/workflows/straightforward.json`) defines `"iteration": { "max": 4 }` for the reviewer. This means the test will actually allow up to 4 reviewer iterations, not 1.

This is not a runtime bug (the test will still work), but it is **misleading**. The test KDoc and implementation summary claim a 15-minute timeout "accommodates two GLM agent sessions (doer + reviewer)", but in reality the reviewer could iterate up to 4 times, spawning additional doer sessions each time.

**Suggested fix**: Either:
- (a) Change to `--iteration-max 4` to match reality and adjust timeout expectations, or
- (b) Add a comment in the test explaining that `--iteration-max` is currently not consumed (DEFERRED) and the workflow JSON's `max: 4` governs actual behavior.

Option (b) is simpler and more transparent.

---

## SHOULD FIX

### 2. Temp directory uses system temp, not `.tmp/`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt` (line 68)

**Issue**: The test creates the temp directory via `Files.createTempDirectory("shepherd-e2e-straightforward-")`, which places it in the system temp directory (e.g., `/tmp/`). CLAUDE.md states:

> Temp files: Write to `$PWD/.tmp/`, NOT `/tmp/`.

**Suggested fix**: Create the temp dir under `projectRoot.resolve(".tmp/e2e-straightforward-${System.currentTimeMillis()}")` and create parent directories. This also makes debugging easier since all artifacts are co-located.

### 3. `listTmuxSessions()` is a free-floating function

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt` (line 285)

**Issue**: CLAUDE.md Kotlin standards say "Disfavor non-private free-floating functions. Favor cohesive classes; for stateless utilities, use a static class." The `listTmuxSessions()` function is a private top-level function.

This is minor since it is `private`, but for consistency it would be better placed as a `companion object` method or a private helper inside the class.

**Suggested fix**: Move into the `companion object` of the test class.

### 4. `runGitInTemp` discards all output including errors

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt` (line 74-81)

**Issue**: `runGitInTemp` redirects both stdout and stderr to `DISCARD`. If `git init` or `git commit` fails, the test will have no diagnostic information. The function returns the exit code but the callers do not check it -- all calls are fire-and-forget.

**Suggested fix**: Either:
- (a) Check the return value and `require(exitCode == 0) { "git ${args.joinToString(" ")} failed" }`, or
- (b) Redirect stderr to a capture stream and include it in the error message.

Option (a) is simpler and sufficient. The test should fail hard if git setup fails rather than continuing with a broken repo.

---

## NICE TO HAVE

### 5. `listTmuxSessions()` swallows all exceptions

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt` (line 294)

**Issue**: `catch (_: Exception) { emptyList() }` catches all exceptions, including unexpected ones like `SecurityException` or `OutOfMemoryError` (though the latter is an `Error`, not `Exception`). This is acceptable for the "before test" snapshot where tmux may not be running, but could mask issues.

**Consideration**: Since this is only used for cleanup delta calculation, this is low-risk. No change needed, but worth noting.

### 6. Consider adding an anchor point to the test class

The test is a key integration test and could benefit from an anchor point for cross-referencing. Other integration tests reference various APs for traceability.

### 7. Missing env var: `TMPDIR`

Some systems respect `TMPDIR` for git operations. If the binary or git uses `TMPDIR` internally, clearing the environment might cause unexpected behavior. This is unlikely to be an issue in Docker but worth noting.

---

## Plan Adherence

| Plan / Reviewer Adjustment | Status |
|---|---|
| Added `./gradlew :app:installDist` to `test_with_integ.sh` | Done |
| Assertions focus on harness behavior (exit code, .ai_out, branch) | Done |
| 15-minute timeout | Done |
| Minimal CLAUDE.md in temp repo | Done |
| Extends `AsgardDescribeSpec` (NOT `SharedContextDescribeSpec`) | Done |
| `@OptIn(ExperimentalKotest::class)` and `isIntegTestEnabled()` gate | Done |
| Delta-based tmux session cleanup | Done |
| Diagnostic log capture on failure | Done |
| GLM env vars on ProcessBuilder | Done |
| Fail hard if binary not built | Done |

---

## Verdict

**APPROVED WITH MINOR REVISIONS**

The implementation is solid and follows the plan well. The MUST FIX item (#1 -- `--iteration-max` documentation) is not a runtime correctness issue but a transparency/accuracy issue. The SHOULD FIX items are code quality improvements. None block the test from working correctly when run in the proper environment.
