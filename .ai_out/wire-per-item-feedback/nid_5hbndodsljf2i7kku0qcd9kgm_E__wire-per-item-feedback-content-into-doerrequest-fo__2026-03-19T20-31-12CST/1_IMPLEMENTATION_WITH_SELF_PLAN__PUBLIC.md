# Wire Per-Item Feedback Content into DoerRequest

## Summary

Added `DoerFeedbackItemRequest` as a new sealed subtype of `AgentInstructionRequest` so that `ContextForAgentProvider` can assemble per-item feedback instructions for the doer agent in the inner feedback loop.

Previously, `InnerFeedbackLoop.buildFeedbackItemRequest()` created a generic `DoerRequest` with `reviewerPublicMdPath=null`, losing the feedback file content, path, and severity. Now it creates a `DoerFeedbackItemRequest` carrying an `InstructionSection.FeedbackItem` with the full feedback context.

## Changes

### New sealed subtype: `DoerFeedbackItemRequest`
- Added to `AgentInstructionRequest` in `ContextForAgentProvider.kt`
- Contains `feedbackItem: InstructionSection.FeedbackItem` instead of `reviewerPublicMdPath`
- Has same common fields as `DoerRequest` (role, ticket, iteration, output, privateMd, executionContext)

### Plan builder: `buildDoerFeedbackItemPlan()`
- Added to `ContextForAgentProviderImpl.kt`
- Same structure as `buildDoerPlan()` but replaces `IterationFeedback` section with the per-item `FeedbackItem`

### Updated `InnerFeedbackLoop`
- `buildFeedbackItemRequest()` now accepts `feedbackFile`, `feedbackContent`, `isOptional` and returns `DoerFeedbackItemRequest`
- `processFeedbackItem()` reads feedback file content before re-instructing doer, passes it to the request builder

### Exhaustiveness: all `when` expressions updated
- `ContextForAgentProviderImpl.assembleInstructions()` — new dispatch branch
- `ContextForAgentProviderImpl.executionContextOrNull` — new branch
- `InstructionSection.PartContext.render()` — new branch
- `InstructionSection.PlanMd.render()` — new branch
- `InstructionSection.PriorPublicMd.render()` — new branch

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/.../context/ContextForAgentProvider.kt` | Added `DoerFeedbackItemRequest` sealed subtype |
| `app/src/main/kotlin/.../context/ContextForAgentProviderImpl.kt` | Added when-branch + `buildDoerFeedbackItemPlan()` + exhaustiveness fix |
| `app/src/main/kotlin/.../context/InstructionSection.kt` | Added `DoerFeedbackItemRequest` branches to 3 `when` expressions |
| `app/src/main/kotlin/.../executor/InnerFeedbackLoop.kt` | Updated `buildFeedbackItemRequest()` signature/body + `processFeedbackItem()` call site |
| `app/src/test/kotlin/.../context/ContextTestFixtures.kt` | Added `doerFeedbackItemRequest()` fixture |
| `app/src/test/kotlin/.../context/ContextForAgentProviderAssemblyTest.kt` | Added 8 assembly tests for feedback item request |
| `app/src/test/kotlin/.../context/InstructionSectionOrderingTest.kt` | Added section ordering test for feedback item request |
| `app/src/test/kotlin/.../executor/InnerFeedbackLoopTest.kt` | Added 7 tests for `buildFeedbackItemRequest` + capturing test; fixed 2 existing tests |

## Test Results

All 1279+ tests pass (0 failures, 4 skipped integration tests). Detekt static analysis passes.

## Decisions

- **New sealed subtype over nullable fields**: Chose `DoerFeedbackItemRequest` (Option B from exploration) to leverage compiler exhaustiveness and keep `DoerRequest` clean.
- **feedbackFileReader reads twice**: `processFeedbackItem()` now reads the feedback file content once before re-instructing (for the instruction assembly) and the existing read after the doer responds (for resolution parsing). This is intentional — the first read captures the original feedback, the second captures the doer's resolution.
