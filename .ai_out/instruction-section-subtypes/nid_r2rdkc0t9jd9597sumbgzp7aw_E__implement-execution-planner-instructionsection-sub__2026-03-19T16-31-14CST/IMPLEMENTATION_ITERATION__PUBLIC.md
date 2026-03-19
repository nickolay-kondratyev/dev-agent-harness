# Implementation Iteration: Address Review Feedback

## Summary

Added 3 missing fail-hard tests. Evaluated and rejected both suggestions.

## MUST FIX Items (3/3 completed)

### 1. PlanMd fail-hard test -- ADDED
Test: `GIVEN a PlanMd section with a DoerRequest and non-null planMdPath pointing to missing file`
- Creates a DoerRequest with `planMdPath` pointing to a non-existent file
- Verifies `shouldThrow<IllegalStateException>` on render

### 2. PriorPublicMd fail-hard test -- ADDED
Test: `GIVEN a PriorPublicMd section with a DoerRequest and missing prior file`
- Creates a DoerRequest with `priorPublicMdPaths` containing a non-existent path
- Verifies `shouldThrow<IllegalStateException>` on render

### 3. IterationFeedback fail-hard test -- ADDED
Test: `GIVEN an IterationFeedback section with a DoerRequest and missing reviewer file`
- Creates a DoerRequest with `reviewerPublicMdPath` pointing to a non-existent file
- Verifies `shouldThrow<IllegalStateException>` on render

## EVALUATE Items

### 1. DRY: Extract `executionContextOrNull` helper -- REJECTED

**Reasoning:** The `when` block appears 3 times but each use extracts different data:
- `PartContext`: extracts `executionContext` itself
- `PlanMd`: extracts `executionContext.planMdPath`
- `PriorPublicMd`: extracts `executionContext.priorPublicMdPaths`

Only two of the three even share the same intermediate step. Each `when` is ~5 lines, clear in isolation, and the exhaustive sealed-type match provides compile-time safety. Adding the extension property would save ~4 lines per use but introduces indirection for a minor boilerplate duplication. Not worth the added complexity.

### 2. Compaction tag ordering assertion -- REJECTED

**Reasoning:** The current test verifies both tags and the guidance text are present. The ordering is structurally guaranteed by the `buildString` implementation which appends lines sequentially. Adding `indexOf` comparisons would be over-engineering -- the test already captures the intended behavior. If someone reordered the `appendLine` calls, the test would likely still fail on content checks, and more importantly, the code structure makes such reordering improbable.

## Verification

- `./test.sh` -- PASS (exit 0)
- All existing tests unmodified
- Detekt clean (shortened one describe string that exceeded max line length)

## Files Modified

- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` -- added 3 fail-hard tests
