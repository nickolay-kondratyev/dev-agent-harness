---
id: nid_8ts4qxw2wevxwep3yk2gvqwja_E
title: "CODE_CONSISTENCY_FIX: ContextForAgentProvider — redesign to sealed AgentInstructionRequest per spec"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T16:05:59Z
status_updated_iso: 2026-03-18T16:52:27Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
---

## Problem

The current `ContextForAgentProvider` interface uses a flat `UnifiedInstructionRequest` data class with nullable role-specific fields plus a separate `AgentRole` enum discriminator. This deviates from the spec design.

**Spec (doc/core/ContextForAgentProvider.md / ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) requires:**

```kotlin
interface ContextForAgentProvider {
    suspend fun assembleInstructions(request: AgentInstructionRequest): Path
}

sealed class AgentInstructionRequest {
    data class DoerRequest(...) : AgentInstructionRequest()
    data class ReviewerRequest(...) : AgentInstructionRequest()
    data class PlannerRequest(...) : AgentInstructionRequest()
    data class PlanReviewerRequest(...) : AgentInstructionRequest()
}

data class ExecutionContext(partName, partDescription, planMdPath?, priorPublicMdPaths)
```

**Current code has:**
```kotlin
interface ContextForAgentProvider {
    suspend fun assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path
}
enum class AgentRole { DOER, REVIEWER, PLANNER, PLAN_REVIEWER }
data class UnifiedInstructionRequest(... many nullable fields ...)
```

## Why It Matters

- **Compile-time safety**: sealed subtype IS the discriminator. Passing doer-only fields to a planner request is a build error, not a runtime null.
- **Simplified call site**: PartExecutor constructs the right subtype directly — no separate role param to sync.
- **Extensible**: new role = new sealed subtype + new plan list.

## Additional: PrivateMd section missing

The spec InstructionPlan for every role includes a `PrivateMd` section (self-compaction context from prior session). Current impl omits it. Should be included in the redesign.

Spec InstructionPlan:
```
Doer: [RoleDefinition, PrivateMd, PartContext, Ticket, ...]
Reviewer: [RoleDefinition, PrivateMd, PartContext, ...]
Planner: [RoleDefinition, PrivateMd, Ticket, ...]
PlanReviewer: [RoleDefinition, PrivateMd, Ticket, ...]
```

`PrivateMd`: path = `${sub_part}/private/PRIVATE.md`. Silently skipped if file does not exist.

## Files to Change
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- All tests in `app/src/test/kotlin/com/glassthought/shepherd/core/context/`
- Any callers (PartExecutor when implemented)

## Acceptance Criteria

1. Interface has single method `assembleInstructions(request: AgentInstructionRequest): Path`
2. AgentInstructionRequest is a sealed class with 4 subtypes: DoerRequest, ReviewerRequest, PlannerRequest, PlanReviewerRequest
3. ExecutionContext composition used for shared execution fields (partName, partDescription, planMdPath, priorPublicMdPaths)
4. ReviewerRequest.doerPublicMdPath and feedbackDir are non-nullable
5. PlannerRequest fields are non-nullable
6. PlanReviewerRequest fields are non-nullable
7. PrivateMd section added to all 4 role plans (silently skipped if file absent)
8. All existing tests pass with updated API
9. Compile-time: passing wrong fields to wrong role is a build error


## Notes

**2026-03-18T18:21:21Z**

Scope clarification: Criterion 7 (PrivateMd in all role plans) — this ticket should only declare the plan position for PrivateMd (i.e., include it in the instruction plan lists). The actual PrivateMd InstructionSection renderer implementation belongs to nid_7vpbal1qdmrvt23g44vpq6hgv_E (InstructionSection engine + shared sections). If implementing before the InstructionSection engine exists, use a placeholder/inline approach that will be replaced when the engine lands.

**2026-03-18T18:50:17Z**

## PrivateMd path source (from review)

All AgentInstructionRequest subtypes should include a `privateMdPath: Path?` field.
- null when no prior session exists (first spawn, no compaction)
- Non-null pointing to `${sub_part}/private/PRIVATE.md` when session rotation occurred

The caller (PartExecutor) knows if compaction happened and sets this field accordingly.
The PrivateMd InstructionSection (ticket nid_7vpbal1qdmrvt23g44vpq6hgv_E) reads from this path if non-null, skips silently if null.

This field belongs in the abstract base of AgentInstructionRequest (all roles can be compacted).

**2026-03-18T19:03:04Z**

## IMPORTANT: DoerRequest needs feedback-loop fields

DoerRequest must include two nullable fields for the inner feedback loop (decided in nid_gp9rduvxoqf14m95z9bttnaxq_E notes):
- `feedbackItemPath: Path?` — non-null during inner feedback loop, null during outer iteration
- `feedbackItemIsOptional: Boolean?` — whether the current feedback item is optional__ (enables SKIPPED resolution)

These fields determine which conditional sections render (FeedbackItem vs IterationFeedback). Add them now to avoid re-opening this ticket later.

Source: doc/core/ContextForAgentProvider.md row 7a + doc/plan/granular-feedback-loop.md (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E)
