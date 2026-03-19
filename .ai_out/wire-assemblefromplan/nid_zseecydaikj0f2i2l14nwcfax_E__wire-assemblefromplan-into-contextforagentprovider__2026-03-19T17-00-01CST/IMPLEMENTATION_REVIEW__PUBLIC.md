# Implementation Review: Wire assembleFromPlan into ContextForAgentProviderImpl

## Verdict: pass

All tests pass (`./test.sh` and `sanity_check.sh`). The implementation satisfies all acceptance criteria.
Two should-fix items noted below for follow-up.

---

## What Passed

### AC Verification (all 12 items)

1. **`assembleInstructions` dispatches via sealed `when` -- no `else` branch** (`ContextForAgentProviderImpl.kt` lines 38-43). Compiler enforces exhaustiveness. Adding a new `AgentInstructionRequest` subtype without a plan is a build error.

2. **Per-role plan lists match spec tables exactly.** Verified all four plans (`buildDoerPlan`, `buildReviewerPlan`, `buildPlannerPlan`, `buildPlanReviewerPlan`) against the concatenation tables in `doc/core/ContextForAgentProvider.md`. Every section is present, in the correct order, with correct conditional logic.

3. **Old `build*Sections()` methods deleted.** No references to `buildDoerSections`, `buildReviewerSections`, `buildPlannerSections`, or `buildPlanReviewerSections` exist in the codebase.

4. **`AgentRole` enum deleted.** No references found anywhere in `*.kt` files.

5. **`UnifiedInstructionRequest` deleted.** No references found.

6. **All existing test behaviors preserved.** The diff shows only two test assertion updates (PriorPublicMd heading format change from `## filename` to `## Prior Output N: filename`) and new test additions. No test cases were removed.

7. **Compiler enforces exhaustiveness.** The sealed `when` dispatch has no `else` branch.

8. **All tests pass.** Both `./test.sh` and `sanity_check.sh` exit 0.

9. **Section ordering matches spec.** New `InstructionSectionOrderingTest` verifies all four roles with index-based ordering assertions.

10. **`InstructionRenderers.kt` deleted.** No file found on disk, no references in `*.kt` files. Rendering logic properly absorbed into `InstructionSection` subtypes.

11. **`FeedbackDirectorySection` uses reviewer's `feedbackDir`** for `addressed/`, `rejected/`, and `pending/` subdirectories (`ContextForAgentProviderImpl.kt` lines 83, 89, 97).

12. **Output path convention enforced.** `InstructionPlanAssembler` writes to `request.outputDir/instructions.md`.

### Architecture Quality

- **SRP well maintained.** `ContextForAgentProviderImpl` selects the plan; `InstructionPlanAssembler` renders it. Each `InstructionSection` subtype owns its own rendering.
- **DRY.** Shared sections (Ticket, WritingGuidelines, CallbackHelp, etc.) have exactly one implementation. No duplication across plan builders.
- **Compile-time safety.** Sealed types for both `AgentInstructionRequest` (role dispatch) and `InstructionSection` (section rendering) prevent runtime surprises.
- **Clean deletion.** No dead code, no dangling imports, no orphaned constants.
- **`InlineStringContentSection` is well-designed.** Clean separation of file-backed vs string-backed content with optional code block wrapping.

### Test Quality

- New `InstructionSectionOrderingTest` covers all four roles with index-based relative ordering.
- New `InlineStringContentSection` tests cover both plain and code-block-wrapped variants.
- PriorPublicMd heading test assertions updated to match new numbered format.
- Existing keyword tests, assembly tests, and section unit tests remain intact.

---

## Issues

### should-fix: `FeedbackDirectorySection.heading` is dead when `headerBody` is set

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt`, lines 504-523

When `headerBody` is non-null, the `heading` field is completely ignored in `render()`:

```kotlin
return if (headerBody != null) {
    "$headerBody\n\n$joinedContent"  // heading is not used
} else {
    "## $heading\n\n$joinedContent"
}
```

All three callers in `ContextForAgentProviderImpl` (lines 82-101) pass both `heading` and `headerBody`, and the `headerBody` values from `InstructionText` already contain `## Addressed Feedback (addressed)` etc. as their first line. This means `heading` is always dead in production use.

**Risk:** A future caller might pass `heading` expecting it to appear alongside `headerBody`, and be surprised when it is silently ignored. This violates POLS (Principle of Least Surprise).

**Suggested fix:** Either:
- (a) Make `heading` and `headerBody` mutually exclusive via a sealed type or factory methods, or
- (b) Always use `heading` as the heading and `headerBody` as the paragraph body below it:
  ```kotlin
  val headerText = if (headerBody != null) "$headerBody" else "## $heading"
  return "$headerText\n\n$joinedContent"
  ```

This is not blocking because the current callers work correctly, but it should be cleaned up to prevent future confusion.

### should-fix: Ordering test assertions could be more diagnostic on failure

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionOrderingTest.kt`

The ordering tests assert:
```kotlin
indices.none { it == -1 } shouldBe true
indices.zipWithNext().all { (a, b) -> a < b } shouldBe true
```

On failure, this produces `false shouldBe true` with no indication of WHICH section was missing or out of order. Consider using a more descriptive assertion pattern, such as:

```kotlin
val sectionNames = listOf("# Role:", "## Part Context", "# Ticket", ...)
val indexPairs = sectionNames.zip(indices)
indexPairs.forEach { (name, idx) ->
    idx shouldNotBe -1  // "Section '$name' not found"
}
indices.zipWithNext().forEachIndexed { i, (a, b) ->
    (a < b) shouldBe true  // "'${sectionNames[i]}' at $a should appear before '${sectionNames[i+1]}' at $b"
}
```

This provides actionable failure messages when a section is missing or misordered.

---

## Summary

Solid implementation. The procedural `build*Sections` pattern is cleanly replaced with data-driven plan lists. The sealed `when` dispatch provides compile-time exhaustiveness. All spec-defined section orderings are correct. Dead code is fully removed. Tests are comprehensive and no existing behavior was lost. The two should-fix items are minor maintainability improvements that do not block merging.
