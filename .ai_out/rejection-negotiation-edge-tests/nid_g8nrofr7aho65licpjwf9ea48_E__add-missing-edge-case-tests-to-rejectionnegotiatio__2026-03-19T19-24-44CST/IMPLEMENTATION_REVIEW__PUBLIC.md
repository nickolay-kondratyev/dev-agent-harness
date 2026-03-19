# Implementation Review: Edge Case Tests 9-13 for RejectionNegotiationUseCaseImpl

## Verdict: PASS

## Summary

Added 5 new test scenarios (Tests 9-13) covering previously untested production branches in `RejectionNegotiationUseCaseImpl`. All 5 required tests are present, correctly exercise the target production code paths, and follow existing test patterns. No production code was modified. All tests pass. Sanity check passes.

### Tests Added
| Test | Scenario | Branch Covered | Correct? |
|------|----------|---------------|----------|
| 9 | Reviewer sends Done(COMPLETED) | Lines 131-137 (`handleReviewerResponse` COMPLETED branch) | Yes |
| 10 | Doer writes SKIPPED after insistence | Lines 175-179 (`FeedbackResolution.SKIPPED` branch) | Yes |
| 11 | Doer writes invalid marker (MAYBE_LATER) | Lines 187-191 (`ParseResult.InvalidMarker` branch) | Yes |
| 12 | Exact call count (2 calls: reviewer then doer) | Structural verification of orchestration order | Yes |
| 13 | feedbackFilePath forwarded to feedbackFileReader | Line 95 path forwarding | Yes |

## No CRITICAL Issues

No security, correctness, or data loss concerns.

## No IMPORTANT Issues

No architecture violations or maintainability concerns.

## Observations (not blocking)

1. **Test 12 call count accumulation across `it` blocks**: Each `it` block calls `sut.execute` independently, and `FakeReInstructAndAwait` is instantiated once in the `describe` block. The `calls` list accumulates across `it` blocks (first `it` adds 2 calls, second `it` adds 2 more, third adds 2 more). This works because:
   - First `it` asserts `calls.size shouldBe 2` -- passes (2 calls).
   - Second `it` asserts `calls[0].first shouldBe reviewerHandle` -- passes (index 0 is still the reviewer from the first execution).
   - Third `it` asserts `calls[1].first shouldBe doerHandle` -- passes (index 1 is still the doer from the first execution).

   This is technically correct but fragile -- if test execution order ever changes or if Kotest runs `it` blocks in isolation, the accumulation pattern could cause index-based assertions to break. However, this matches the existing pattern used throughout the file (Tests 1-8 all share `FakeReInstructAndAwait` instances across `it` blocks the same way), so this is consistent. No action needed.

2. **Test 13 uses a custom `FeedbackFileReader` instead of `buildFileReader`**: This is the right call -- `buildFileReader` ignores the path parameter (`_ ->`), so a capturing reader is needed to verify path forwarding. Clean approach.

3. **All assertions follow one-assert-per-`it` pattern**: Consistent with CLAUDE.md standards.

4. **BDD GIVEN/WHEN/THEN structure**: Properly nested, clear descriptions.

## Files Modified

- `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt` (173 lines added, 0 removed)

## Verification

- `./gradlew :app:test --tests "*RejectionNegotiationUseCaseImplTest"` -- BUILD SUCCESSFUL
- `./sanity_check.sh` -- EXIT_CODE=0
- No pre-existing tests removed or modified
- No production code changes
