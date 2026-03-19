# Implementation Review: Wire Per-Item Feedback Content into DoerRequest

## Summary

This change adds `DoerFeedbackItemRequest` as a new sealed subtype of `AgentInstructionRequest` so that
the inner feedback loop can provide per-item feedback content to the doer agent through `ContextForAgentProvider`.
Previously, `buildFeedbackItemRequest()` created a generic `DoerRequest` with `reviewerPublicMdPath=null`,
losing all feedback context. Now it creates a dedicated `DoerFeedbackItemRequest` carrying an
`InstructionSection.FeedbackItem` with the feedback content, file path, and severity.

**Overall assessment: APPROVE.** The implementation is clean, follows existing patterns correctly,
leverages compiler exhaustiveness for safety, and has thorough test coverage. No blocking issues found.

## CRITICAL Issues

None.

## IMPORTANT Issues

None.

## Suggestions

### 1. DRY: Repeated `executionContext` extraction in `InstructionSection.kt` when-expressions

Three `when` expressions in `InstructionSection.kt` (`PartContext.render`, `PlanMd.render`,
`PriorPublicMd.render`) plus `executionContextOrNull` in `ContextForAgentProviderImpl.kt` all
duplicate the same pattern of extracting `executionContext` from DoerRequest/DoerFeedbackItemRequest/ReviewerRequest
and returning null for PlannerRequest/PlanReviewerRequest. Each new sealed subtype with an
`executionContext` will require updating all 4 places.

This is a **pre-existing pattern** that this PR merely extends (correctly). A follow-up ticket could
extract a shared `executionContextOrNull` extension function on `AgentInstructionRequest` and reuse
it across all 4 call sites. This would reduce the blast radius of future sealed subtype additions.

**Relevant files:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` (lines 77-83, 226-232, 256-262)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` (lines 191-198)

### 2. DRY: `buildDoerPlan` vs `buildDoerFeedbackItemPlan` are nearly identical

The two plan builders in `ContextForAgentProviderImpl` share 9 of 10 lines, differing only in the
feedback section (`IterationFeedback` vs `request.feedbackItem`). This is a minor concern --
the duplication is in declarative plan lists which serve as readable documentation of concatenation
order. Extracting a shared method would add indirection that may hurt readability. The current
approach is acceptable given these are stable, short lists.

**Relevant file:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` (lines 54-67 vs 77-90)

## What Was Done Well

1. **Sealed subtype approach (Option B)**: Correct architectural choice. Using a new sealed subtype
   instead of nullable fields on `DoerRequest` leverages the compiler to enforce exhaustiveness.
   Every `when` expression was updated, and the compiler would catch any that were missed.

2. **Clean SRP separation**: Per-item feedback is a distinct use case from iteration feedback.
   Having `DoerFeedbackItemRequest` carry an `InstructionSection.FeedbackItem` (which already
   existed and was fully implemented) keeps the data model clean.

3. **Thorough test coverage**: 8 new assembly tests, 1 section ordering test, 7 new
   `buildFeedbackItemRequest` unit tests, and 1 capturing integration test that verifies the
   correct request type flows through `processFeedbackItem` to `ContextForAgentProvider`.
   The queue-based `FeedbackFileReader` in the capturing test is a smart approach for testing
   the two-read pattern.

4. **Existing tests updated correctly**: The 2 existing tests (ReInstruct crash/FailedWorkflow
   propagation) were updated to provide `FakeFeedbackFileReader` content, which is required now
   that `processFeedbackItem` reads content before re-instructing. No test behavior was removed.

5. **Good documentation**: KDoc on `DoerFeedbackItemRequest` clearly explains how it differs
   from `DoerRequest`, references the spec anchor point, and `buildDoerFeedbackItemPlan` explains
   its relationship to `buildDoerPlan`.

## Documentation Updates Needed

None. The changes are self-contained and the existing spec references (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E)
are properly linked.
