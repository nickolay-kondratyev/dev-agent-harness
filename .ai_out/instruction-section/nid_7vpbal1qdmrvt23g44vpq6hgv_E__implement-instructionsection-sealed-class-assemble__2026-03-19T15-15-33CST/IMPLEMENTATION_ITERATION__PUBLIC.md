# Implementation Iteration 1: InstructionSection Review Feedback

## Issue 1: OutputPathSection rendering diverges from existing behavior

**Status: ADDRESSED**

Added expanded KDoc on `OutputPathSection` documenting that the unified template (`## $label Output Path` / `Write your $label to:`) is an **intentional design decision**. The previous per-role rendering differences (e.g. `## Output Path` vs `## PUBLIC.md Output Path`, `human-readable plan` vs file name) were accidental inconsistency. The new class provides a single canonical template for all output paths going forward.

No structural code changes needed -- the rendering logic itself is correct per the spec which says `OutputPathSection(label, path)` is a unified replacement for all prior output path sections.

**File changed:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` (KDoc on `OutputPathSection`)

## Issue 2: Test assertion contradicts its description

**Status: ADDRESSED**

Fixed the test at line 268-270 in `InstructionSectionTest.kt`. The test named "THEN does NOT include validate-plan query" was incorrectly asserting `result shouldContain "Communicating with the Harness"` (a positive check on unrelated text). Changed to `result shouldNotContain "validate-plan"` which correctly verifies the negative case described by the test name. Added the missing `shouldNotContain` import.

**File changed:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt`

## Reviewer Suggestions (not actioned)

1. **`result!!` in tests**: Acknowledged as minor style point. Not changed -- the reviewer agreed tests work correctly as-is, and the `!!` pattern is consistent across the test file.
2. **Temp directory cleanup**: Acknowledged. Not changed -- standard for short-lived test processes and not a real issue per the reviewer's own assessment.

## Test Results

All tests pass (`./gradlew :app:test` -- PASS).
