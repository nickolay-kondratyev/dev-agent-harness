# Reviewer Private Notes

## Review Methodology

1. Read exploration and implementation plan docs for context.
2. Read the new test file in full (621 lines).
3. Read the production code being tested: `InnerFeedbackLoop.kt`, `PartCompletionGuard.kt`, `RejectionNegotiationUseCase.kt` (full impl).
4. Read the existing unit test `InnerFeedbackLoopTest.kt` to understand pre-existing coverage and identify true integration value.
5. Ran `./sanity_check.sh` -- PASSED.
6. Ran `./test.sh` -- PASSED (all tests green).
7. Checked git diff from main -- only test file + ai_out files changed, no production code modified.

## Key Observations

### What the integration test adds beyond unit tests
- The unit test (`InnerFeedbackLoopTest`) fakes `RejectionNegotiationUseCase` as a lambda. The integration test wires real `RejectionNegotiationUseCaseImpl`, which means the full rejection negotiation flow (reviewer judgment -> doer compliance -> resolution re-parsing) is exercised end-to-end.
- This validates that `QueueBasedFeedbackFileReader`'s multi-read semantics match the actual read patterns of `InnerFeedbackLoop` + `RejectionNegotiationUseCaseImpl` working together. If either component changes its read order, the test will break -- good integration coverage.

### QueueBasedFeedbackFileReader correctness
- The queue-based approach is clever but fragile: if the production code changes the number of reads per file (e.g., adds a validation read), tests will break with "Content queue exhausted" rather than a clear failure message. This is acceptable for an integration test -- the brittleness IS the integration signal.
- One concern: in Scenario 1 (line 260), the `Files.list()` iteration order is filesystem-dependent. The `for (file in Files.list(pending).use { it.toList() })` loop enqueues content in whatever order the FS returns files, but since all files get the same content pattern, the order doesn't matter. No bug here.

### Self-compaction gap
- The `InnerFeedbackLoop.processFeedbackItem()` reads `contextWindowState` at line 175-176, but it only LOGS it (debug). The actual self-compaction decision is made OUTSIDE InnerFeedbackLoop (at the PartExecutor level or higher). So the integration test may not have a meaningful self-compaction path to test at this layer.
- However, the ticket requirement is explicit. Flagged as IMPORTANT so the implementer can either add it or document why it's excluded.

### Pair usage
- Clear CLAUDE.md violation. Easy fix. Flagged as IMPORTANT.

### One-assert-per-test violations
- Scenario 1's first `it` block and the Mixed Flow scenario have multiple conceptually distinct assertions. The project standard is strict on this. Flagged as IMPORTANT but not critical since the assertions are thematically related.

### Scenario 4 isolation concern
- The PartCompletionGuard tests in Scenario 4 don't go through InnerFeedbackLoop at all. They're pure unit tests of PartCompletionGuard placed in an integration test file. This isn't wrong, but it inflates the perception of "integration coverage" without actually testing the guard's integration with the loop.
