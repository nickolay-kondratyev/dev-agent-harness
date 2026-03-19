# Implementation: 7 Role-Specific InstructionSection Subtypes

## What Was Done

Added 7 new subtypes to the `InstructionSection` sealed class (sections 8-14), extending the existing 7 shared subtypes:

### Execution-Specific (Doer/Reviewer)

1. **PlanMd** (data object) — Reads `ExecutionContext.planMdPath`, renders under `# Plan` heading. Null path = skip. Planner/PlanReviewer = skip. Missing file = fail hard.

2. **PriorPublicMd** (data object) — Renders each file from `ExecutionContext.priorPublicMdPaths` under a heading with the filename. Empty list = skip. Planner/PlanReviewer = skip. Missing file = fail hard.

3. **IterationFeedback** (data object) — DoerRequest only. Renders reviewer's PUBLIC.md content under `## Reviewer Feedback`, appends `DOER_PUSHBACK_GUIDANCE` wrapped in `<critical_to_keep_through_compaction>` tags. Null reviewerPublicMdPath (iteration 1) = skip.

4. **InlineFileContentSection** (data class: heading, path?) — Generic file-to-section renderer. Null path = skip. Non-null + missing = fail hard. Renders as `## $heading\n\n<content>`.

### Planner-Specific

5. **RoleCatalog** (data object) — PlannerRequest only. Delegates to `InstructionRenderers.roleCatalog()`.

6. **AvailableAgentTypes** (data object) — Returns static `InstructionText.AGENT_TYPES_AND_MODELS` for any request type.

7. **PlanFormatInstructions** (data object) — PlannerRequest only. Returns static `InstructionText.PLAN_FORMAT_INSTRUCTIONS`.

## Tests Added

All tests in `InstructionSectionTest.kt` following existing BDD patterns:

- **InlineFileContentSection**: existing file renders, missing file throws, null path returns null
- **PlanMd**: DoerRequest with path renders, null path skips, PlannerRequest skips
- **PriorPublicMd**: non-empty paths render each file, empty list skips, PlannerRequest skips, negative test verifying only listed files render
- **IterationFeedback**: non-null path renders feedback + compaction-wrapped pushback guidance, null path skips, ReviewerRequest skips
- **RoleCatalog**: PlannerRequest renders, DoerRequest returns null
- **AvailableAgentTypes**: returns static text for any request type, includes V1 constraints
- **PlanFormatInstructions**: PlannerRequest renders, DoerRequest returns null

## Files Modified

- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` — 7 new sealed subtypes
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` — ~250 lines of new tests

## Decisions

- Used `as?` smart cast + `?.` chaining for `IterationFeedback` to keep return count under detekt's limit of 2.
- Used `when` + `?.takeIf {}` + `?: return null` pattern for `PriorPublicMd` to collapse null-from-planner and empty-list cases into a single return.
- `PlanMd` collapses planner-null and doer-null-path into one `?: return null` after the `when` expression.
