# Plan Review: ContextForAgentProvider Sealed Redesign

## Executive Summary

The plan is well-structured, correctly reflects the spec, and is implementable as-is by an IMPLEMENTATION agent. The sealed hierarchy design is faithful to the spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E), the PrivateMd path derivation is correct per the `.ai_out/` directory schema (ref.ap.BXQlLDTec7cVVOrzXWfR7.E), and the test strategy is sound. Two minor issues found; both are correctable inline without plan iteration.

## Critical Issues (BLOCKERS)

None.

## Major Concerns

None.

## Minor Issues (corrected inline below)

### 1. PrivateMd position differs between spec and plan for Doer/Reviewer

- **Issue:** The spec's InstructionPlan for Doer and Reviewer places PrivateMd at position 2 (after RoleDefinition, before PartContext). The plan correctly states "insert after role definition (position 2)" in Phase 2, Step 8. However, the spec places PrivateMd at position 2 for Planner/PlanReviewer as well (after RoleDefinition, before Ticket) -- the plan says the same. This is consistent. No issue here on closer inspection.

### 2. Reviewer iteration-1 fixture: feedbackDir must become non-nullable

- **Issue:** The `reviewerInstructionRequest` fixture currently sets `feedbackDir = null`. With the sealed redesign, `feedbackDir` on `ReviewerRequest` is non-nullable.
- **Status:** The plan correctly addresses this in Phase 3, Step 2: "Create an empty directory for iteration-1 fixtures." This is sound because `collectFeedbackFiles` already handles empty directories gracefully.
- **No action needed.**

### 3. Debug logging update could be simpler

- **Concern:** Phase 2, Step 7 proposes using `request::class.simpleName` for the role name and a `when` to extract `partName`. This works but is slightly more ceremony than needed.
- **Suggestion:** `request::class.simpleName` is fine and sufficient. For partName, consider extracting via a simple `(request as? AgentInstructionRequest.DoerRequest)?.executionContext?.partName ?: (request as? AgentInstructionRequest.ReviewerRequest)?.executionContext?.partName` or even better, a small extension:
  ```kotlin
  private val AgentInstructionRequest.executionContextOrNull: ExecutionContext?
      get() = when (this) {
          is AgentInstructionRequest.DoerRequest -> executionContext
          is AgentInstructionRequest.ReviewerRequest -> executionContext
          is AgentInstructionRequest.PlannerRequest -> null
          is AgentInstructionRequest.PlanReviewerRequest -> null
      }
  ```
  But this is stylistic -- the plan's `when` approach works equally well. **Implementation agent can decide.**

## Simplification Opportunities (PARETO)

- **None identified.** The plan is already well-scoped. It changes exactly the 7 files needed, no more. The sealed hierarchy is the minimum viable type-safe design.

## Spec-Code Gap Check

| Gap | Plan Addresses? |
|-----|----------------|
| `AgentRole` enum -> sealed `AgentInstructionRequest` | Yes -- Phase 1 |
| `UnifiedInstructionRequest` -> 4 typed subtypes | Yes -- Phase 1 |
| `ExecutionContext` composition | Yes -- Phase 1, Step 3 |
| Non-nullable `doerPublicMdPath` on ReviewerRequest | Yes -- Phase 1, Step 4 |
| Non-nullable `feedbackDir` on ReviewerRequest | Yes -- Phase 1, Step 4 |
| Non-nullable planner fields | Yes -- Phase 1, Step 4 |
| Non-nullable plan reviewer fields | Yes -- Phase 1, Step 4 |
| `PrivateMd` section in all 4 role plans | Yes -- Phase 2, Step 8 |
| Remove all `requireNotNull` guards | Yes -- Phase 2, Step 4 |
| Sealed `when` without `else` | Yes -- Phase 2, Step 2 |
| Interface signature change | Yes -- Phase 1, Step 5 |
| Test updates for removed `AgentRole` param | Yes -- Phase 4 |
| Delete 4 guard tests (now compile-time) | Yes -- Phase 4a, Step 3 |
| PrivateMd tests | Yes -- Phase 5 |

All 9 acceptance criteria from the ticket are covered by the plan.

## Testing Strategy Assessment

- **Guard tests correctly deleted:** The 4 `requireNotNull` guard tests become unrepresentable with sealed types -- correct to remove.
- **PrivateMd test coverage:** Plan proposes testing with doer + one other role. This is sufficient -- the `privateMdSection` method is shared, so verifying it is wired in 2 of 4 roles gives high confidence.
- **Existing behavioral tests preserved:** All structural and keyword tests are updated, not deleted. Good.
- **`.copy()` migration:** Plan correctly identifies that `copy()` on `ExecutionContext` fields requires nested copy -- e.g., `baseRequest.copy(executionContext = baseRequest.executionContext.copy(planMdPath = ...))`. This is slightly more verbose but is the natural consequence of composition and is correct.

## Strengths

- **Faithful to spec:** The sealed hierarchy exactly matches the spec's `AgentInstructionRequest` definition including field names, nullability, and comments.
- **Self-contained scope:** Confirmed zero external callers of `AgentRole` or `UnifiedInstructionRequest` outside `core/context`.
- **Correct path derivation:** `outputDir.parent.parent.resolve("private/PRIVATE.md")` is verified against the `.ai_out/` directory schema: `outputDir = ${sub_part}/comm/in`, so `parent.parent = ${sub_part}`, and the spec places `PRIVATE.md` at `${sub_part}/private/PRIVATE.md`.
- **Clear implementation order:** The recommended sequence (Phase 1 -> 3 -> 2 -> 4 -> 5) correctly handles compilation dependencies.
- **Risk table is accurate:** Especially the medium-risk on PrivateMd path derivation -- test-first approach mitigates this.
- **No over-engineering:** The plan does not introduce the full `InstructionSection` sealed class / `assembleFromPlan` data-driven engine from the spec. It correctly limits scope to the sealed request hierarchy + PrivateMd addition. The data-driven assembly engine is a separate, larger refactor.

## Verdict

- [x] APPROVED WITH MINOR REVISIONS

**Minor revisions (non-blocking, implementation agent discretion):**
1. The debug logging approach (Step 7) is left to implementation agent's judgment -- both `when`-based extraction and the extension property approach are acceptable.

**Plan iteration: NOT NEEDED.** The plan is implementable as-is.
