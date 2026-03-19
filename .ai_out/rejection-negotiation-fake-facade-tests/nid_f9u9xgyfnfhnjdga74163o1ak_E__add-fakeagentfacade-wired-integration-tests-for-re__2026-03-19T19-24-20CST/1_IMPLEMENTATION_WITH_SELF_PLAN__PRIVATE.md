# Implementation Private Notes

## Status: COMPLETE

All 8 test scenarios pass. Full test suite green.

## Key Decision

The wired test revealed a real production bug: `RejectionNegotiationUseCaseImpl` passed message content (multi-line strings with em-dashes) to `ReInstructAndAwait.execute()` which internally calls `Path.of(message)`. This fails with `InvalidPathException` on systems with ASCII native encoding.

Fixed by introducing `InstructionFileWriter` functional interface — follows the same pattern as `FeedbackFileReader` (functional interface for filesystem access, fakeable in tests).

## Deviation from Plan

Original plan was to only add tests. Had to also fix production code to make the wired composition actually work. This is the value of wired tests — they catch integration bugs that unit tests with fakes miss.

## Files Changed

- NEW: `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/InstructionFileWriter.kt`
- MODIFIED: `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt`
- MODIFIED: `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt`
- NEW: `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationWithFakeAgentFacadeTest.kt`

## Follow-up

- Ticket `nid_srtovyxkmpyp3xupve7x1akiy_E` created for the original bug (now fixed). Can be closed with this change.
- Production `InstructionFileWriter` implementation needed when wiring at top-level entry point (writes to `.ai_out/` or temp directory).
