# Implementation: ContextForAgentProvider Sealed Redesign

## Summary

Implemented the sealed `AgentInstructionRequest` hierarchy per spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E), replacing the flat `UnifiedInstructionRequest + AgentRole` enum dispatch. Added `PrivateMd` section to all 4 role instruction plans.

## Changes Made

### Production Code (2 files)

**`app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`**
- Deleted `enum class AgentRole` and `data class UnifiedInstructionRequest`
- Added `data class ExecutionContext` (composition for shared doer+reviewer fields)
- Added `sealed class AgentInstructionRequest` with 4 subtypes: `DoerRequest`, `ReviewerRequest`, `PlannerRequest`, `PlanReviewerRequest`
- Updated interface method: `assembleInstructions(request: AgentInstructionRequest): Path` (no `role` parameter)
- `RoleCatalogEntry` unchanged

**`app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`**
- Sealed `when` dispatch (no `else` branch -- compiler-enforced exhaustiveness)
- Each `build*Sections` method takes its specific sealed subtype
- Removed all `requireNotNull` guards (now compile-time type safety)
- Added `privateMdSection(outputDir)` method: reads `outputDir.parent.parent.resolve("private/PRIVATE.md")`, returns null if absent
- PrivateMd inserted at position 2 (after RoleDefinition) in all 4 role plans
- Added `executionContextOrNull` extension property for debug logging
- Updated reviewer feedback conditional: `feedbackDir` is always non-null, only check `iterationNumber > 1`
- Fixed detekt MaxLineLength on `buildPlanReviewerSections` signature

### Test Code (5 files)

**`app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt`**
- All fixture methods return concrete sealed subtypes instead of `UnifiedInstructionRequest`
- `reviewerInstructionRequest`: `feedbackDir` is now non-nullable (creates empty temp directory)
- `doerInstructionRequest`: wraps execution fields in `ExecutionContext`

**`app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt`**
- Removed `AgentRole.*` first argument from all `assembleInstructions()` calls
- Updated `.copy()` calls for `ExecutionContext` nesting (e.g., `baseRequest.copy(executionContext = baseRequest.executionContext.copy(planMdPath = ...))`)
- Deleted 4 `requireNotNull` guard tests (states now unrepresentable)
- Added 3 PrivateMd tests: doer with PRIVATE.md present, doer without PRIVATE.md, planner with PRIVATE.md

**`ExecutionAgentInstructionsKeywordTest.kt`**, **`PlannerInstructionsKeywordTest.kt`**, **`PlanReviewerInstructionsKeywordTest.kt`**
- Removed `AgentRole.*` from `assembleInstructions` calls
- Replaced `!!` operators with direct field access (fields now non-nullable on sealed subtypes)

## Design Decisions

1. **`executionContextOrNull` extension**: Used for debug logging to extract partName from doer/reviewer requests without duplicating `when` blocks at call sites. Chosen over the plan's inline `when` approach per reviewer suggestion.

2. **PrivateMd path derivation**: `outputDir.parent.parent.resolve("private/PRIVATE.md")` where `outputDir = ${sub_part}/comm/in`, giving `${sub_part}/private/PRIVATE.md` per spec.

3. **Reviewer feedbackDir on iteration 1**: Changed from `null` to empty directory. The `collectFeedbackFiles` implementation already handles empty directories gracefully, so no behavioral change.

## Acceptance Criteria Verification

1. All existing tests pass (with updated API) -- `./gradlew :app:test` green
2. Compile-time: constructing `ReviewerRequest` without `doerPublicMdPath` is a build error
3. Compile-time: constructing `PlannerRequest` without `planJsonOutputPath` is a build error
4. Compile-time: passing `partName` to `PlannerRequest` is impossible (field doesn't exist)
5. No `requireNotNull` calls remain in `ContextForAgentProviderImpl` for role-specific fields
6. PrivateMd section present when file exists, absent when file doesn't exist (3 tests)
7. `AgentRole` enum no longer exists
8. `UnifiedInstructionRequest` no longer exists
9. Detekt passes
