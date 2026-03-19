# Review Private Context

## What I Checked

1. **Sanity check**: `./sanity_check.sh` -- PASS
2. **Unit tests**: `./gradlew :app:test --tests InstructionSectionTest` -- PASS
3. **Diff analysis**: Verified diff is purely additive (no lines removed from production or test code vs main)
4. **Anchor points**: No APs removed; sealed class AP `ap.YkR8mNv3pLwQ2xJtF5dZs.E` preserved
5. **Existing tests**: All 7 original section tests intact, 7 new section tests added
6. **DRY analysis**: Identified `when` pattern duplication (executionContext extraction) x3 -- flagged as suggestion
7. **Fail-hard verification**: All 3 new file-reading sections (PlanMd, PriorPublicMd, IterationFeedback) have `check()` calls, but only InlineFileContentSection has a corresponding fail-hard test. Flagged as IMPORTANT.

## Risk Assessment

- **Low risk**: All code is purely additive, no behavioral changes to existing code
- **Sealed class exhaustiveness**: All `when` blocks cover all 4 request types without `else` -- compiler enforced
- **File I/O**: Uses `check()` for fail-hard which is idiomatic Kotlin; throws `IllegalStateException`

## Decision Rationale for Severity

- Missing fail-hard tests: IMPORTANT (not CRITICAL) because the production code is correct and the `check()` calls are straightforward. But the test gap means a future refactor could silently remove the guard without test failure.
- DRY suggestion: Suggestion only because the duplication is minor (3 occurrences) and each is self-contained within its section.
