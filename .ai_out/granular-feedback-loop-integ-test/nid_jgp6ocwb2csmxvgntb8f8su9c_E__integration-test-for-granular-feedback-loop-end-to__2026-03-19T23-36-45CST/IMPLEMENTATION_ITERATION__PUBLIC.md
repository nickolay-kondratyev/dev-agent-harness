# Review Feedback Iteration -- Results

## Changes Made

### 1. Replaced `Pair` with `WiredLoopSetup` data class (IMPORTANT)

Introduced `data class WiredLoopSetup(val loop: InnerFeedbackLoop, val gitStrategy: RecordingGitStrategy)` inside the test class. Updated `buildWiredLoop()` to return `WiredLoopSetup` instead of `Pair<InnerFeedbackLoop, RecordingGitStrategy>`. All 6 call sites updated -- destructuring continues to work via data class component functions.

### 2. Self-compaction documentation (IMPORTANT)

Added a WHY-NOT comment in the class KDoc explaining that `InnerFeedbackLoop` reads context window state per item but does NOT own the compaction decision (that belongs to `PartExecutorImpl.afterDone()`). There is no integration surface to test self-compaction at this layer. The unit test `InnerFeedbackLoopTest` verifies that `readContextWindowState` is called per item, which is the extent of this component's responsibility.

### 3. Split multi-assertion `it` blocks (IMPORTANT)

**Scenario 1 (happy path):** Extracted setup into `beforeEach`, split into 4 focused `it` blocks:
- `THEN result is Continue`
- `THEN all files move to addressed/ in severity order`
- `THEN pending/ is empty`
- `THEN git commit is called once per feedback item`

**Mixed flow scenario:** Same pattern -- `beforeEach` for setup, split into 5 focused `it` blocks:
- `THEN result is Continue`
- `THEN critical and optional land in addressed/`
- `THEN rejected important item lands in rejected/`
- `THEN pending/ is empty`
- `THEN git commit is called once per feedback item`

## Rejected Suggestions

- **Extract duplicated test helpers**: Test infrastructure duplication between `GranularFeedbackLoopIntegTest` and `InnerFeedbackLoopTest` is acknowledged but deferred. DRY is much less important in tests than in business rules per CLAUDE.md.
- **Move PartCompletionGuard tests**: These tests validate the guard in context of the wired integration, adding value beyond unit tests.

## Test Results

All tests pass: `./gradlew :app:test` exits with code 0.

## File Modified

- `app/src/test/kotlin/com/glassthought/shepherd/integtest/feedback/GranularFeedbackLoopIntegTest.kt`
