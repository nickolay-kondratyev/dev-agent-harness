# Exploration: Wire Per-Item Feedback Content into DoerRequest

## Gap
`InnerFeedbackLoop.processFeedbackItem()` has the feedback file path, content, and severity — but `buildFeedbackItemRequest()` creates a generic `DoerRequest` without any of this data. `ContextForAgentProviderImpl` therefore cannot include the `InstructionSection.FeedbackItem` in the instruction plan.

## Key Files
| File | What |
|------|------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt` | `processFeedbackItem()` (line ~161), `buildFeedbackItemRequest()` (line ~443) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` | `AgentInstructionRequest` sealed class (line ~56), `DoerRequest` (line ~73) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` | `assembleInstructions()` when-dispatch, `buildDoerPlan()` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` | `FeedbackItem` data class (line ~396) — already fully implemented |

## Recommended Approach: New Sealed Subtype (Option B)
Add `DoerFeedbackItemRequest` as a new sealed subtype of `AgentInstructionRequest` rather than adding nullable fields to `DoerRequest`. This:
- Follows SRP: per-item feedback is a distinct use case from standard iteration feedback
- Leverages compiler exhaustiveness: adding a sealed subtype forces when-expression updates
- Keeps `DoerRequest` clean (no feedback-loop-specific concerns)

## Changes Required
1. **ContextForAgentProvider.kt**: Add `DoerFeedbackItemRequest` sealed subtype with `feedbackItem: InstructionSection.FeedbackItem` field
2. **ContextForAgentProviderImpl.kt**: Add when-branch + `buildDoerFeedbackItemPlan()` method (same as doer plan but with FeedbackItem instead of IterationFeedback)
3. **InnerFeedbackLoop.kt**: Update `buildFeedbackItemRequest()` to accept feedback file path/content/isOptional and return `DoerFeedbackItemRequest`; update `processFeedbackItem()` call site to read file content and pass it

## Existing Infrastructure (Reused)
- `InstructionSection.FeedbackItem` — fully implemented, just needs to be added to plan
- `InstructionPlanAssembler` — unchanged
- Section rendering pipeline — unchanged
