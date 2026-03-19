# Implementation Iteration: NonInteractiveAgentRunner Review Feedback

## Summary

Addressed 3 of 4 review items (skipped item 4 per instructions -- low ROI shell escaping test).

## Changes Made

### 1. Replaced `Pair` with named data class (IMPORTANT -- CLAUDE.md compliance)

**File**: `app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImplTest.kt`

- Added `private data class RunnerWithFake(val runner: NonInteractiveAgentRunnerImpl, val fakeProcessRunner: FakeProcessRunner)` at file scope.
- Changed `buildRunner()` return type from `Pair<...>` to `RunnerWithFake`.
- Changed `.first` access to `.runner` at all call sites.
- Destructuring `val (runner, fake)` continues to work via data class `componentN()` functions.

### 2. Added timeout forwarding test (IMPORTANT -- spec contract coverage)

Added test in the "GIVEN a CLAUDE_CODE agent request / WHEN run is called" block:

```kotlin
it("THEN the request timeout is forwarded to ProcessRunner") {
    runner.run(buildRequest(agentType = AgentType.CLAUDE_CODE, timeout = 20.minutes))
    fake.lastTimeout shouldBe 20.minutes
}
```

This ensures the spec contract (Section "Execution", step 3) is covered: timeout must be forwarded to `processRunner.runProcessV2`.

### 3. Replaced `is` checks with `shouldBeInstanceOf` (nice-to-have -- better failure messages)

Replaced 3 occurrences of:
```kotlin
(result is NonInteractiveAgentResult.Success) shouldBe true
```
with:
```kotlin
result.shouldBeInstanceOf<NonInteractiveAgentResult.Success>()
```

Applied to Success, Failed, and TimedOut type assertions.

### 4. Shell escaping test with variable expansion protection -- SKIPPED

Per instructions, skipped as low ROI.

## Test Results

All tests pass: `./gradlew :app:test` -- BUILD SUCCESSFUL.

Total test count: 20 (up from 19, +1 timeout forwarding test).
