# Gate 6: Granular Feedback Loop Integration Test -- Review

## Summary

The test file `GranularFeedbackLoopIntegTest.kt` adds a "wired integration test" that validates the granular feedback loop components work together end-to-end. It wires real `InnerFeedbackLoop`, `RejectionNegotiationUseCaseImpl`, `PartCompletionGuard`, and `FeedbackResolutionParser` instances, faking only agent communication boundaries (`ReInstructAndAwait`, `FeedbackFileReader`, `FakeAgentFacade`, `GitCommitStrategy`).

All 10 tests pass. `./test.sh` and `./sanity_check.sh` are green.

**Overall assessment**: Solid wired integration test. Covers the 7 required ticket scenarios plus a bonus mixed-flow scenario. The test structure is well-organized and follows project BDD conventions. There are two substantive issues below.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `Pair` usage in `buildWiredLoop` violates Kotlin standards

**File**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/feedback/GranularFeedbackLoopIntegTest.kt`, line 189

```kotlin
fun buildWiredLoop(
    ...
): Pair<InnerFeedbackLoop, RecordingGitStrategy> {
```

CLAUDE.md explicitly states: "No `Pair`/`Triple` -- create descriptive `data class`." This is used 6 times across the test scenarios via destructuring `val (loop, gitStrategy) = buildWiredLoop(...)`.

**Fix**: Create a small data class:

```kotlin
data class WiredLoopSetup(
    val loop: InnerFeedbackLoop,
    val gitStrategy: RecordingGitStrategy,
)
```

And return `WiredLoopSetup(loop, gitStrategy)` instead.

### 2. Self-compaction scenario not tested (ticket requirement gap)

The ticket explicitly lists "Self-compaction triggers between items when context is low" as one of the 7 required scenarios. The test stubs `ContextWindowState(remainingPercentage = 80)` everywhere, meaning self-compaction never triggers. The `IMPLEMENTATION_WITH_SELF_PLAN__PUBLIC.md` does not list self-compaction as a covered scenario either.

The existing unit test (`InnerFeedbackLoopTest`) verifies `readContextWindowState` is called per item, but that is a different concern -- it tests that the check **happens**, not that compaction **triggers** when context is low.

Since the integration test wires real `InnerFeedbackLoop`, a scenario with low `remainingPercentage` (e.g., 10%) would validate that the compaction path integrates correctly with the rest of the feedback loop. If self-compaction triggers a session rotation that interacts with `ReInstructAndAwait` or other components, that wiring is currently untested.

**Recommendation**: Either add a self-compaction scenario or document in the test why it was deliberately excluded (e.g., if self-compaction is fully handled upstream of InnerFeedbackLoop and there is no integration surface to test here).

### 3. Multiple assertions per `it` block in Scenario 1 and Mixed Flow

The first `it` block ("THEN all files move to addressed/ in severity order", line 249) has 4 assertions:
- `result shouldBe InnerLoopOutcome.Continue`
- `addressedFiles shouldContainExactly ...`
- `listFiles(pending) shouldHaveSize 0`
- `gitStrategy.calls shouldHaveSize 3`

The mixed flow scenario (line 563) similarly has 5 assertions in one `it` block.

CLAUDE.md says "Each `it` block contains **one logical assertion**." While these assertions are closely related, the git commit count assertion is testing a distinct concern from file placement. Consider splitting, at minimum, the git commit count assertions into their own `it` blocks.

---

## Suggestions

### 1. DRY: Duplicated helper methods with `InnerFeedbackLoopTest`

The following helpers are nearly identical between `GranularFeedbackLoopIntegTest` and `InnerFeedbackLoopTest`:
- `buildHandle()` (lines 61-76 vs unit test lines 38-53)
- `createFeedbackDir()` (lines 81-87 vs unit test lines 86-92)
- `writePendingFile()` (lines 89-97 vs unit test lines 95-103)
- `RecordingGitStrategy` (lines 131-136 vs unit test lines 116-121)
- `fakeContextProvider()` (lines 163-167 vs unit test lines 142-146)
- `listFiles()` (lines 230-240)

These could be extracted to a shared test utility class (e.g., `FeedbackTestFixtures`). Not blocking for this PR, but worth a follow-up ticket to reduce future maintenance burden.

### 2. Temp file cleanup

`createFeedbackDir()` and `createDoerConfigWithPublicMd()` use `Files.createTempDirectory()` without registering cleanup. These rely on OS temp dir cleanup. While not a correctness issue (tests are isolated by unique temp dirs), adding `afterSpec` cleanup or using a Kotest temp dir extension would be cleaner.

### 3. Scenario 4 (PartCompletionGuard) tests the guard in isolation

The PartCompletionGuard tests (lines 417-464) instantiate `PartCompletionGuard()` directly and test it without going through `InnerFeedbackLoop`. This is effectively a unit test placed inside an integration test file. The integration value would be higher if the guard were tested through the full loop (e.g., having the reviewer signal PASS while critical items remain in pending). Consider whether this belongs here or in the existing `PartCompletionGuardTest`.

---

## Documentation Updates Needed

None required for CLAUDE.md. The test is correctly placed in the `integtest/feedback/` package and follows existing conventions.
