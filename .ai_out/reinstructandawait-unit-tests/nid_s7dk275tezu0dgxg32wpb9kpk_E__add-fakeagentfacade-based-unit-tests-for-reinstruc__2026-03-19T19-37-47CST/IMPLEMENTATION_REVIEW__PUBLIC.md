# Review: Add FakeAgentFacade-based Unit Tests for ReInstructAndAwaitImpl

## Summary

Added 4 new test scenarios (8 new `it` blocks) to `ReInstructAndAwaitImplTest.kt` exercising FakeAgentFacade interaction verification. All 20 tests pass (12 existing + 8 new). No existing tests were removed or broken. The `buildHandle()` helper gained an optional `guidValue` parameter (backward-compatible default).

Overall assessment: **APPROVE with suggestions.** The implementation is correct, covers all 4 ticket scenarios, and follows project conventions. Two DRY issues and one one-assert-per-test violation are worth addressing but are not blockers.

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. Scenario 3 (multiple handles) shares a FakeAgentFacade across `it` blocks -- state leaks between tests

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-7/app/src/test/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwaitImplTest.kt`
**Lines:** 265-288

The `fakeFacade`, `sut`, `doerHandle`, and `reviewerHandle` are constructed in the `describe` block body (not suspend context), and the facade accumulates `sendPayloadCalls` across `it` blocks. If the first `it` block runs before the second, the facade will have 2 calls from the first test plus 2 more from the second (4 total), and `sendPayloadCalls[1]` in the second test will be the reviewer from the *first* test run, not the second.

This happens to work today because both `it` blocks independently call `execute(doerHandle, ...)` then `execute(reviewerHandle, ...)` and check index `[0]` and `[1]` respectively. But the shared mutable state means the second `it` block actually has 4 entries in `sendPayloadCalls`, and `[1]` happens to be the reviewer from the first `it` run. This is **fragile and misleading** -- the test "works" but for the wrong reason if execution order changes.

**Fix:** Follow the same pattern used in Scenario 1 -- build a fresh `FakeAgentFacade` inside each `it` block (or extract a local builder function like `buildSequentialFacade()`).

```kotlin
describe("GIVEN two different SpawnedAgentHandles") {
    describe("WHEN execute is called once per handle") {
        it("THEN first sendPayloadCall references the doer handle") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.Done(DoneResult.COMPLETED)
            }
            val sut = buildSut(fakeFacade)
            val doerHandle = buildHandle(guidValue = "handshake.doer")
            val reviewerHandle = buildHandle(guidValue = "handshake.reviewer")
            sut.execute(doerHandle, "/tmp/doer-instruction.md")
            sut.execute(reviewerHandle, "/tmp/reviewer-instruction.md")
            fakeFacade.sendPayloadCalls[0].handle shouldBe doerHandle
        }
        // ... similarly for second it block
    }
}
```

### 2. One-assert-per-test violation in Scenario 1

**File:** same test file
**Lines:** 232-240

The `it("THEN both calls reference the same handle")` block contains **two** assertions (checking `[0].handle` and `[1].handle`). Per CLAUDE.md testing standards, each `it` block should have one logical assertion.

**Fix:** Split into two `it` blocks:
- `"THEN first call references the same handle"`
- `"THEN second call references the same handle"`

## Suggestions

### 1. DRY -- Scenario 1 has significant setup duplication

Each of the 4 `it` blocks in Scenario 1 (lines 205-241) repeats the same 4-line setup:
```kotlin
val fakeFacade = buildSequentialFacade()
val sut = buildSut(fakeFacade)
val handle = buildHandle()
sut.execute(handle, firstMessage)
sut.execute(handle, secondMessage)
```

This is intentional (each `it` needs isolated state due to the `ArrayDeque`), and the project guidelines say DRY is "much less important in tests." However, consider extracting a small data class or helper that returns the facade post-execution to reduce the 5-line block to 1-2 lines. This is optional.

### 2. Scenario 2 is largely redundant with the existing "GIVEN execute is called with a message" tests

The existing test at lines 166-186 already verifies:
```kotlin
sentPayload shouldBe AgentPayload(instructionFilePath = Path.of(testMessage))
```

Scenario 2 (lines 246-261) verifies essentially the same thing with a different path string. The only difference is that Scenario 2 checks `.instructionFilePath` directly instead of the whole `AgentPayload`. This provides marginal additional value. Not a problem, just noting that the coverage overlap is high.

### 3. Scenario 2 shares FakeAgentFacade state between `describe` and `it` -- same pattern as Scenario 3

Lines 248-253 set up the facade in the `describe` block. This works here because there is only one `it` block, so no state leakage occurs. But if someone adds a second `it` block later, the same issue as Scenario 3 would surface. Consider moving setup into the `it` block for consistency with Scenario 1.

## Documentation Updates Needed

None required.

## Verdict

**APPROVE with two requested changes:**
1. Fix the shared mutable FakeAgentFacade state in Scenario 3 (IMPORTANT #1)
2. Split the two-assertion `it` block in Scenario 1 (IMPORTANT #2)
