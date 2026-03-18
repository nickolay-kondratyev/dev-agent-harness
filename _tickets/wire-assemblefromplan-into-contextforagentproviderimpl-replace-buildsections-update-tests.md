---
id: nid_zseecydaikj0f2i2l14nwcfax_E
title: "Wire assembleFromPlan into ContextForAgentProviderImpl — replace build*Sections + update tests"
status: open
deps: [nid_r2rdkc0t9jd9597sumbgzp7aw_E, nid_gp9rduvxoqf14m95z9bttnaxq_E, nid_8ts4qxw2wevxwep3yk2gvqwja_E]
links: [nid_7vpbal1qdmrvt23g44vpq6hgv_E, nid_gp9rduvxoqf14m95z9bttnaxq_E, nid_r2rdkc0t9jd9597sumbgzp7aw_E]
created_iso: 2026-03-18T18:18:07Z
status_updated_iso: 2026-03-18T18:18:07Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ContextForAgentProvider, InstructionSection, migration]
---

## Goal

Replace the current 4 `build*Sections()` methods in `ContextForAgentProviderImpl` with the data-driven `assembleFromPlan` pattern, as specified in `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E).

This is the final integration ticket that wires the InstructionSection engine and all section subtypes into the provider, replacing the procedural code.

## What to Do

### 1. Define per-role InstructionPlan lists

As specified in the spec:

```
Doer:         [RoleDefinition, PrivateMd, PartContext, Ticket, PlanMd, PriorPublicMd,
               IterationFeedback, FeedbackItem,
               OutputPathSection("PUBLIC.md"), WritingGuidelines, CallbackHelp]

Reviewer:     [RoleDefinition, PrivateMd, PartContext, Ticket, PlanMd, PriorPublicMd,
               InlineFileContentSection("Doer Output"), StructuredFeedbackFormat,
               FeedbackDirectorySection("addressed"), FeedbackDirectorySection("rejected"),
               FeedbackDirectorySection("remaining optional"),
               FeedbackWritingInstructions,
               OutputPathSection("PUBLIC.md"), WritingGuidelines, CallbackHelp]

Planner:      [RoleDefinition, PrivateMd, Ticket, RoleCatalog, AvailableAgentTypes,
               PlanFormatInstructions, InlineFileContentSection("Reviewer Feedback"),
               OutputPathSection("plan_flow.json"), OutputPathSection("PLAN.md"),
               OutputPathSection("PUBLIC.md"),
               WritingGuidelines, CallbackHelp]

PlanReviewer: [RoleDefinition, PrivateMd, Ticket,
               InlineFileContentSection("plan_flow.json"), InlineFileContentSection("PLAN.md"),
               AvailableAgentTypes, InlineFileContentSection("Planner PUBLIC.md"),
               InlineFileContentSection("Prior Feedback"),
               OutputPathSection("PUBLIC.md"), WritingGuidelines, CallbackHelp]
```

### 2. Replace assembleInstructions dispatch

```kotlin
suspend fun assembleInstructions(request: AgentInstructionRequest): Path {
    val plan = when (request) {
        is AgentInstructionRequest.DoerRequest         -> doerPlan
        is AgentInstructionRequest.ReviewerRequest     -> reviewerPlan
        is AgentInstructionRequest.PlannerRequest      -> plannerPlan
        is AgentInstructionRequest.PlanReviewerRequest -> planReviewerPlan
    }
    return assembleFromPlan(plan, request)
}
```

### 3. Remove old code

- Delete `buildDoerSections()`, `buildReviewerSections()`, `buildPlannerSections()`, `buildPlanReviewerSections()`
- Verify `AgentRole` enum already removed by `nid_8ts4qxw2wevxwep3yk2gvqwja_E` (remove if still present)
- Verify `UnifiedInstructionRequest` already removed by `nid_8ts4qxw2wevxwep3yk2gvqwja_E` (remove if still present)

### 4. Update all tests

Existing test files to migrate:
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ExecutionAgentInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlannerInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlanReviewerInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt`

All tests must use the new `AgentInstructionRequest` subtypes instead of `AgentRole` + `UnifiedInstructionRequest`.

## Files to Change

- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` — major rewrite
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` — update interface signature, remove AgentRole, remove UnifiedInstructionRequest
- All test files listed above
- Potentially remove `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionRenderers.kt` if all logic is absorbed into InstructionSection subtypes

## Spec Reference
- `doc/core/ContextForAgentProvider.md` — "Internal Design: Data-Driven Assembly" section, "assembleFromPlan" section

## Acceptance Criteria

1. `assembleInstructions` dispatches via sealed `when` on `AgentInstructionRequest` — no `else` branch
2. Per-role plan lists defined as specified
3. Old `build*Sections` methods deleted
4. `AgentRole` enum deleted
5. `UnifiedInstructionRequest` deleted
6. All existing test behaviors preserved (same content assertions, updated API calls)
7. The `when` dispatch on `AgentInstructionRequest` has no `else` branch — compiler enforces exhaustiveness (adding a sealed subtype without a plan entry is a build error)
8. All tests pass via `./test.sh`


## Notes

**2026-03-18T18:40:58Z**

GAP from review: Add explicit AC for end-to-end section ordering verification — after wiring, add tests that assemble full instructions.md for each of the 4 roles and verify the section sequence matches the spec (Doer: RoleDefinition→PrivateMd→PartContext→Ticket→..., etc). Existing migrated tests may not verify complete plan ordering since they were written against the old flat API. Suggested AC: 'For each role, an integration-style test assembles a full instructions.md and verifies sections appear in spec-defined order.'
