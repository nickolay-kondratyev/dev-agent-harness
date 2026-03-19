# Implementation Review: 7 Role-Specific InstructionSection Subtypes

## Summary

Added 7 new sealed subtypes (8-14) to `InstructionSection`: `PlanMd`, `PriorPublicMd`, `IterationFeedback`, `InlineFileContentSection`, `RoleCatalog`, `AvailableAgentTypes`, `PlanFormatInstructions`. All tests pass (sanity check + unit tests). No existing tests removed. The implementation is clean, follows existing patterns, and correctly implements the spec.

**Overall assessment: APPROVE with minor suggestions.**

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. Missing fail-hard tests for PlanMd and PriorPublicMd when file is missing

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt`

`InlineFileContentSection` correctly has a test verifying `shouldThrow<IllegalStateException>` when the file is non-null but missing. However, `PlanMd` and `PriorPublicMd` both have identical fail-hard `check()` calls in production code but **no corresponding tests** verifying the exception is thrown.

From the acceptance criteria: "InlineFileContentSection: non-null+present -> renders; non-null+missing -> fails hard". The same fail-hard contract exists in `PlanMd` (line 175-177) and `PriorPublicMd` (line 205-207) but is not tested.

**Fix:** Add two test cases:

```kotlin
// PlanMd fail-hard test
describe("GIVEN a PlanMd section with a DoerRequest and non-null planMdPath pointing to missing file") {
    val tempDir = Files.createTempDirectory("section-planmd-missing-test")
    val missingPlanFile = tempDir.resolve("PLAN.md") // not created
    val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
    val request = baseRequest.copy(
        executionContext = baseRequest.executionContext.copy(planMdPath = missingPlanFile)
    )

    describe("WHEN rendered") {
        it("THEN throws IllegalStateException") {
            shouldThrow<IllegalStateException> {
                InstructionSection.PlanMd.render(request)
            }
        }
    }
}

// PriorPublicMd fail-hard test
describe("GIVEN a PriorPublicMd section with a DoerRequest and missing prior file") {
    val tempDir = Files.createTempDirectory("section-priorpub-missing-test")
    val missingPrior = tempDir.resolve("missing_PUBLIC.md") // not created
    val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
    val request = baseRequest.copy(
        executionContext = baseRequest.executionContext.copy(
            priorPublicMdPaths = listOf(missingPrior)
        )
    )

    describe("WHEN rendered") {
        it("THEN throws IllegalStateException") {
            shouldThrow<IllegalStateException> {
                InstructionSection.PriorPublicMd.render(request)
            }
        }
    }
}
```

### 2. Missing fail-hard test for IterationFeedback when reviewer file is missing

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt`

`IterationFeedback.render()` has a `check(Files.exists(reviewerPath))` at line 231-233, but there is no test for the case where `reviewerPublicMdPath` is non-null but the file does not exist. This fail-hard behavior is important and should be tested.

## Suggestions

### 1. DRY opportunity: Extract executionContext-or-null helper

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt`

The `when` block extracting `executionContext` (returning it for Doer/Reviewer, null for Planner/PlanReviewer) is repeated 3 times with slight variations (lines 78-83, 168-173, 197-202). Consider extracting a private helper on `AgentInstructionRequest`:

```kotlin
// In AgentInstructionRequest or as extension in InstructionSection
private val AgentInstructionRequest.executionContextOrNull: ExecutionContext?
    get() = when (this) {
        is DoerRequest -> executionContext
        is ReviewerRequest -> executionContext
        is PlannerRequest -> null
        is PlanReviewerRequest -> null
    }
```

Then `PlanMd.render` becomes:
```kotlin
val planMdPath = request.executionContextOrNull?.planMdPath ?: return null
```

This is a suggestion, not a blocker -- the duplication is minor and each use is clear in isolation.

### 2. Compaction tags assertion could be more precise

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt`, line 534-537

The test checks that the output `shouldContain` both opening and closing compaction tags, but does not verify that the pushback guidance is actually **inside** the tags (between them). A more precise test could verify ordering:

```kotlin
it("THEN wraps pushback guidance between compaction-survival tags") {
    val openIdx = result!!.indexOf("<critical_to_keep_through_compaction>")
    val guidanceIdx = result!!.indexOf("Handling Reviewer Feedback")
    val closeIdx = result!!.indexOf("</critical_to_keep_through_compaction>")
    (openIdx < guidanceIdx) shouldBe true
    (guidanceIdx < closeIdx) shouldBe true
}
```

This is a minor suggestion -- the current test is reasonable for ensuring the tags are present.

### 3. Test fixture cleanup: temp directories are not cleaned up

All tests create temp directories via `Files.createTempDirectory(...)` but never delete them. This is standard for JVM test runs (OS cleans `/tmp`), so not a real issue, but worth noting.

## Documentation Updates Needed

None -- the KDoc on the sealed class and each subtype is thorough and accurate.

## Verification

- `./sanity_check.sh` -- PASS
- `./gradlew :app:test --tests "com.glassthought.shepherd.core.context.InstructionSectionTest"` -- PASS (all tests green)
- No existing tests removed (diff is purely additive)
- No anchor points removed
- No reference files modified
