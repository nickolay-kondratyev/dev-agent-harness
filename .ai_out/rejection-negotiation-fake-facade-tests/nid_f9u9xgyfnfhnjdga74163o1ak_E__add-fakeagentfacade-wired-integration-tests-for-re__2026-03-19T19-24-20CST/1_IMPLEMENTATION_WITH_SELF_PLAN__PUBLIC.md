# Implementation Summary

## What Was Done

Created `RejectionNegotiationWithFakeAgentFacadeTest` — a wired integration test class that tests the full composition stack: `FakeAgentFacade` -> `ReInstructAndAwaitImpl` -> `RejectionNegotiationUseCaseImpl`.

### Bug Found and Fixed

During wiring, discovered that `RejectionNegotiationUseCaseImpl` was passing multi-line message content to `ReInstructAndAwait.execute()`, which internally calls `Path.of(message)`. This caused `InvalidPathException` on systems with ASCII native encoding (em-dash character).

**Fix**: Added `InstructionFileWriter` functional interface dependency to `RejectionNegotiationUseCaseImpl`. The use case now writes message content to a file via the writer and passes the file path to `ReInstructAndAwait`.

### Test Scenarios (8 total, 18 test assertions)

1. Reviewer signals Done(PASS) -> RejectionResult.Accepted
2. Reviewer NEEDS_ITERATION + doer complies (ADDRESSED) -> AddressedAfterInsistence
3. Reviewer signals Done(COMPLETED) — unexpected -> AgentCrashed
4. Reviewer insists, doer writes SKIPPED -> AgentCrashed
5. Reviewer insists, doer writes invalid marker (MAYBE_LATER) -> AgentCrashed
6. Reviewer signals Crashed -> AgentCrashed propagates
7. Reviewer signals FailWorkflow -> FailedWorkflow propagates
8. Verify sendPayloadCalls recording (2 entries, correct handles)

## Files Modified

- `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/InstructionFileWriter.kt` — NEW: functional interface for writing instruction content to files
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt` — Added `InstructionFileWriter` constructor dependency, uses it to persist messages before passing paths
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt` — Updated to provide `RecordingInstructionFileWriter`, adjusted 2 tests to verify content via writer
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationWithFakeAgentFacadeTest.kt` — NEW: 8 wired test scenarios

## Ticket Created

- `nid_srtovyxkmpyp3xupve7x1akiy_E` — Documents the original path mismatch bug (now fixed in this change)
