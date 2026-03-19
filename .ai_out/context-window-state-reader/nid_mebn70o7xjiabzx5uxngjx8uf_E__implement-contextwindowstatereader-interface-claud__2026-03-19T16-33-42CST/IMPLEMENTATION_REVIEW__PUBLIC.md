# Implementation Review: ContextWindowStateReader

## VERDICT: PASS

## Summary

Clean, well-structured implementation of `ContextWindowStateReader` interface and `ClaudeCodeContextWindowStateReader`. The code follows project conventions: constructor injection, BDD tests with one assert per `it` block, structured logging via `Out`/`Val`, and proper exception hierarchy. All 7 tests pass, full suite is green, sanity check passes.

The implementation correctly handles all specified behaviors:
- Fresh file returns `remainingPercentage`
- Stale file returns `null` with warning
- Missing file throws exception
- Malformed JSON throws exception with details
- Invalid timestamp throws exception
- Boundary condition (exact stale threshold) treated as fresh

## Issues

None blocking.

## Suggestions

### 1. No bounds validation on `remainingPercentage`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/agent/contextwindow/ClaudeCodeContextWindowStateReader.kt` (line 83-87)

The external hook could write a `remaining_percentage` value outside 0-100 (e.g., -1 or 200). Currently this passes through unchecked. Consider adding a validation in `parseAndValidate` that the value is within the expected 0-100 range, or at minimum logging a warning if out of bounds. This is defensive given the value comes from an external process.

### 2. TOCTOU on file existence check

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/agent/contextwindow/ClaudeCodeContextWindowStateReader.kt` (lines 43-48, 96-111)

There is a time-of-check-to-time-of-use gap between `Files.exists(filePath)` on line 43 and `Files.readString(filePath)` on line 98. If the file is deleted between these calls, the `IOException` catch on line 106 will handle it, so this is safe in practice. However, the error message would say "Failed to read" rather than "not found", which is less clear. Low priority given the external hook writes the file and this is not a hot path.

## Praise

- **Nullable DTO + explicit validation pattern** is a smart workaround for Jackson KotlinModule's silent defaulting of missing `Int` fields to 0. Well-documented with the WHY comment in `parseAndValidate`.
- **`ValidatedContextWindowSlim` inner class** cleanly separates the parse phase (nullable) from the validated phase (non-nullable), eliminating downstream null checks.
- **Injectable `basePath`** enables clean testability without any filesystem mocking frameworks.
- **Boundary test** (exact stale threshold) demonstrates careful attention to edge cases and documents the `isBefore` semantics.
- **Exception hierarchy** correctly follows the existing `PlanConversionException` pattern with a clear WHY-NOT comment explaining why `AsgardBaseException` was not used.
- Test helper functions (`buildReader`, `writeJsonFile`) keep test cases focused and DRY.

## Documentation Updates Needed

None. The implementation PUBLIC.md is thorough and the anchor point `ap.ufavF1Ztk6vm74dLAgANY.E` is properly preserved on the interface.
