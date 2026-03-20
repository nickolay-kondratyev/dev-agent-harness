---
closed_iso: 2026-03-20T18:17:46Z
id: nid_maa8lefzvngmu40roa4z23whd_E
title: "Create test_with_e2e.sh and verify E2E straightforward workflow test passes"
status: closed
deps: []
links: []
created_iso: 2026-03-20T16:23:44Z
status_updated_iso: 2026-03-20T18:17:46Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [integration-test, e2e, straightforward]
---

## Context

We just implemented `StraightforwardWorkflowE2EIntegTest` (a black-box E2E integration test)
but have NOT yet verified it actually passes against real agents. We only confirmed it compiles
and unit tests pass.

### What exists
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt`
  — Black-box test that runs actual binary via ProcessBuilder with GLM env vars
- `test_with_integ.sh` — runs ALL integration tests (too broad, includes other integ tests)

## What we want

1. **Create `test_with_e2e.sh`** — a focused script that runs ONLY the E2E test class
   - Should build binary first (`./gradlew :app:installDist`)
   - Should run only `StraightforwardWorkflowE2EIntegTest` (use Gradle test filtering)
   - Example: `./gradlew :app:test -PrunIntegTests=true --tests "com.glassthought.shepherd.integtest.e2e.StraightforwardWorkflowE2EIntegTest"`
   - Include asgard dependency prep (like other test scripts)
   - Output to `.tmp/test_with_e2e.txt`

2. **Run the test and verify it passes** — actually execute the E2E flow
   - If the test fails, debug and fix the issues
   - The test should complete with exit code 0
   - Document any issues found and fixed

### Key files
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt` — the test
- `test_with_integ.sh` — pattern reference for the new script
- `test.sh` — pattern reference for asgard dependency prep
- `_prepare_pre_build.sh` — asgard dependency preparation
- `config/workflows/straightforward.json` — workflow definition
- `run.sh` — binary invocation reference

## Acceptance Criteria

1. ✅ `test_with_e2e.sh` exists and is executable
2. ✅ Running `./test_with_e2e.sh` executes ONLY the E2E test (not all integration tests)
3. ✅ The E2E test passes (exit code 0) against real GLM-backed agents
4. ✅ Any bugs found during execution are fixed (5 bugs found and fixed)

## Resolution

### Created `test_with_e2e.sh`
Follows the exact pattern of `test_with_integ.sh` with `--tests` filter for the E2E class.

### 5 Production Bugs Found and Fixed
1. **SessionsState wiring** — Server and AgentFacade had separate instances; signals couldn't find entries. Fixed by sharing via ShepherdContext.
2. **awaitStartupOrCleanup deadlock** — `handleStarted` never completed `signalDeferred`. Added `AgentSignal.Started`.
3. **GUID not found in JSONL** — `FilesystemGuidScanner` searched JSONL content but GUID was only in env vars. Appended `[HARNESS_GUID: ...]` to bootstrap message.
4. **Iteration 1-based** — `CommitMessageBuilder` required `currentIteration >= 1` but executor started at 0. Fixed with 1-based counting for reviewer path.
5. **ticket CLI PATH** — `ProcessBuilder` can't resolve shell functions. Added note-ticket to subprocess PATH in E2E test.

### Review: PASS
Implementation review confirmed all 5 fixes are correct root-cause fixes (not workarounds). Minor feedback addressed (stale comment, trailing newline).
