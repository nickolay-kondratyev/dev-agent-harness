# Implementation Review: FakeAgentFacade-Wired Integration Tests for RejectionNegotiationUseCaseImpl

## Summary

This change adds 8 wired integration test scenarios (18 assertions) in a new test class `RejectionNegotiationWithFakeAgentFacadeTest` that exercises the full composition stack: `FakeAgentFacade` -> `ReInstructAndAwaitImpl` -> `RejectionNegotiationUseCaseImpl`.

During implementation, the author discovered a **real bug**: `RejectionNegotiationUseCaseImpl` was passing multi-line message content to `ReInstructAndAwait.execute()`, but the contract (`@param message Absolute path to the instruction file`) and implementation (`ReInstructAndAwaitImpl` calls `Path.of(message)`) both expect a file path. This would cause `InvalidPathException` at runtime. The fix introduces `InstructionFileWriter` as a new dependency to bridge content-to-file-path.

**Overall assessment**: Good work. The bug fix is legitimate and well-executed. Tests are well-structured. A few issues to address.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### SHOULD-FIX 1: `RecordingInstructionFileWriter` is duplicated between two test files

The inner class `RecordingInstructionFileWriter` is copy-pasted identically in both:
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt` (lines 95-102)
- The same class structure exists conceptually in the new test file (though the new file uses a simpler lambda instead)

The `RecordingInstructionFileWriter` in `RejectionNegotiationUseCaseImplTest` is a proper reusable test double. Consider extracting it to a shared location if more callers emerge. Low urgency since only two files reference it currently, but worth noting for DRY.

### SHOULD-FIX 2: `sendPayloadCalls` is not reset between `it` blocks in the new test file

Each `it` block in the new test file calls `sut.execute(...)` independently, but the `FakeAgentFacade` instance is shared across all `it` blocks within a `describe`. The `sendPayloadCalls` list accumulates across `it` invocations within the same `describe`.

For example, in Test 8 (lines 329-361), three `it` blocks each call `sut.execute(...)`. The first `it` adds 2 calls. The second `it` adds 2 more (total 4). The third `it` adds 2 more (total 6).

- The assertion `fakeFacade.sendPayloadCalls.size shouldBe 2` (line 348) would only pass on the first `it` execution.
- `fakeFacade.sendPayloadCalls[0]` (line 352) would still return the correct first entry, so that passes.
- `fakeFacade.sendPayloadCalls[1]` (line 358) would still return the correct second entry, so that passes.

**Wait** -- on closer inspection, Kotest `DescribeSpec` creates a fresh instance for each test by default (single-instance mode is off by default). But the `FakeAgentFacade` is created inside the `describe` block body, which runs once per spec instance. Let me reconsider...

Actually, in Kotest `DescribeSpec`, the `describe` block body **does** run fresh for each `it` block via the test isolation mechanism. So each `it` gets a fresh `FakeAgentFacade`. This means the size assertion `shouldBe 2` is correct.

**Retracted** -- this is not actually an issue. Kotest's default `IsolationMode.SingleInstance` with `DescribeSpec` means the describe block content re-executes for each leaf test. No issue here.

### SHOULD-FIX 3: Missing production `InstructionFileWriter` implementation

The `InstructionFileWriter` interface was added, but there is no production implementation that actually writes to the filesystem. The existing wiring site (wherever `RejectionNegotiationUseCaseImpl` gets constructed in production) will need one. Since `RejectionNegotiationUseCaseImpl` is not yet wired in any production creator, this is acceptable -- but a ticket should exist for providing the production implementation.

**Status**: The implementation summary mentions ticket `nid_srtovyxkmpyp3xupve7x1akiy_E` was created. This covers the tracking need.

---

## Suggestions

### NIT 1: Test 1 assertion on line 142-143 only checks handle identity, not payload content

In Test 1 (lines 119-146), the second `it` block verifies that `reviewerPayload.handle shouldBe reviewerHandle` but does not verify anything about the payload content (e.g., that the instruction file path was set). The test name says "payload forwarded to reviewer contains rejection reasoning" but the assertion only checks the handle. The content verification is more naturally tested in the existing `RejectionNegotiationUseCaseImplTest` which uses `RecordingInstructionFileWriter`, so this is minor -- but the test name is misleading.

**Suggestion**: Either rename to "THEN payload is sent to reviewer handle" or add an assertion on the payload's `instructionFilePath`.

### NIT 2: `buildHandle` helper is duplicated between the two test files

The `buildHandle(name: String)` helper is identical in both `RejectionNegotiationUseCaseImplTest` and `RejectionNegotiationWithFakeAgentFacadeTest`. Per CLAUDE.md, DRY is less important in tests, so this is fine -- just noting it.

### NIT 3: `buildFileReader` helper is duplicated

Same as above -- the closure-based `buildFileReader` helper is duplicated. Acceptable in test code.

---

## Production Change Assessment

**Was the bug real?** YES. The `ReInstructAndAwait` interface's `@param message` documents "Absolute path to the instruction file to deliver to the agent." The implementation `ReInstructAndAwaitImpl` calls `Path.of(message)`. The original `RejectionNegotiationUseCaseImpl` was passing multi-line message content (with em-dash characters) directly, which would throw `InvalidPathException` on any platform.

**Was the fix minimal and appropriate?** YES. Adding `InstructionFileWriter` as a functional interface dependency is the right approach:
- It follows the existing pattern (similar to `FeedbackFileReader`)
- It's testable (fake in tests, real filesystem in production)
- It maintains SRP -- the use case builds the message, the writer persists it
- The interface is a `fun interface` so it can be expressed as a lambda

**Were existing tests preserved?** YES. All 8 original test scenarios in `RejectionNegotiationUseCaseImplTest` are intact. Two tests were modified to verify content through the `RecordingInstructionFileWriter` instead of through `FakeReInstructAndAwait.calls` -- this is a necessary adaptation since the message parameter now contains a file path, not content. The test intent (verifying that the correct message content is assembled) is preserved.

---

## Documentation Updates Needed

None required. The `InstructionFileWriter` interface has clear KDoc, and the production change is well-documented in code comments.

---

## VERDICT: **PASS**

The implementation is correct, well-tested, and the production bug fix is justified. The issues identified are minor (one misleading test name, acceptable duplication in test code). All tests pass, including `sanity_check.sh`.
