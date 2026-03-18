# Implementation Review: ContextForAgentProvider Sealed Redesign

## Summary

The implementation correctly replaces the flat `UnifiedInstructionRequest + AgentRole` enum dispatch with a sealed `AgentInstructionRequest` hierarchy per spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). The `PrivateMd` section is added to all 4 role plans. All tests pass. Sanity check passes. Detekt passes.

**Overall assessment: APPROVE.** The implementation is clean, matches the spec precisely, and the test coverage is adequate. The 4 removed `requireNotNull` guard tests are correctly replaced -- those runtime states are now unrepresentable at compile time, which is the entire point of the redesign.

## No CRITICAL Issues

No security, correctness, or data loss issues found.

## No IMPORTANT Issues

No architecture violations or maintainability concerns.

## Acceptance Criteria Verification

| # | Criterion | Status | Notes |
|---|-----------|--------|-------|
| 1 | Interface has single method `assembleInstructions(request: AgentInstructionRequest): Path` | PASS | Verified in `ContextForAgentProvider.kt` line 32 |
| 2 | `AgentInstructionRequest` is sealed class with 4 subtypes | PASS | `DoerRequest`, `ReviewerRequest`, `PlannerRequest`, `PlanReviewerRequest` -- lines 59-111 |
| 3 | `ExecutionContext` composition used for shared fields | PASS | Lines 45-50, used by `DoerRequest` and `ReviewerRequest` |
| 4 | `ReviewerRequest.doerPublicMdPath` and `feedbackDir` are non-nullable | PASS | Lines 84-85 |
| 5 | `PlannerRequest` fields (`planJsonOutputPath`, `planMdOutputPath`) are non-nullable | PASS | Lines 96-97 |
| 6 | `PlanReviewerRequest` fields (`planJsonContent`, `planMdContent`, `plannerPublicMdPath`) are non-nullable | PASS | Lines 106-108 |
| 7 | `PrivateMd` section added to all 4 role plans (silently skipped if file absent) | PASS | Lines 56, 92, 133, 170 in `ContextForAgentProviderImpl.kt` |
| 8 | All existing tests pass with updated API | PASS | `./gradlew :app:test` exits 0 |
| 9 | Compile-time: passing wrong fields to wrong role is a build error | PASS | Sealed subtypes enforce field presence per role |

## Detailed Findings

### Sealed Hierarchy Matches Spec Exactly

The sealed class in `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` matches the spec field-by-field:

- Common abstract properties: `roleDefinition`, `ticketContent`, `iterationNumber`, `outputDir`, `publicMdOutputPath`
- `DoerRequest`: common + `executionContext` + `reviewerPublicMdPath: Path?`
- `ReviewerRequest`: common + `executionContext` + `doerPublicMdPath: Path` + `feedbackDir: Path`
- `PlannerRequest`: common + `roleCatalogEntries` + `planReviewerPublicMdPath: Path?` + `planJsonOutputPath: Path` + `planMdOutputPath: Path`
- `PlanReviewerRequest`: common + `planJsonContent: String` + `planMdContent: String` + `plannerPublicMdPath: Path` + `priorPlanReviewerPublicMdPath: Path?`

All remaining nullables are semantically meaningful (absent on iteration 1 or absent in no-planning workflows).

### PrivateMd Implementation is Correct

Path derivation in `privateMdSection()` at line 211 of `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`:

```kotlin
val privateMdPath = outputDir.parent.parent.resolve("private/PRIVATE.md")
```

Given `outputDir = ${sub_part}/comm/in`, `parent.parent = ${sub_part}`, so the resolved path is `${sub_part}/private/PRIVATE.md` -- matches the spec.

### Removed Tests are Justified

The 4 `requireNotNull` guard tests were removed because the states they tested are now **unrepresentable at compile time**:
- "GIVEN a DOER request with null partName" -- `partName` is now `String` (non-nullable) inside `ExecutionContext`
- "GIVEN a REVIEWER request with null partName" -- same
- "GIVEN a PLANNER request with null planJsonOutputPath" -- `planJsonOutputPath` is now `Path` (non-nullable) in `PlannerRequest`
- "GIVEN a PLAN_REVIEWER request with null planJsonContent" -- `planJsonContent` is now `String` (non-nullable) in `PlanReviewerRequest`

These were replaced with 3 PrivateMd tests covering the new functionality.

### No Old Type References Remain

Grep confirms zero references to `UnifiedInstructionRequest` or `AgentRole` anywhere in `app/src/`.

### `RoleCatalogEntry` Unchanged

Confirmed at lines 120-124 of `ContextForAgentProvider.kt` -- kept as-is per plan.

### Sealed `when` Dispatch Has No `else` Branch

Confirmed at lines 41-46 of `ContextForAgentProviderImpl.kt` -- compiler enforces exhaustiveness.

## Suggestions

### 1. `executionContextOrNull` Extension

The `executionContextOrNull` extension property (lines 335-341 of `ContextForAgentProviderImpl.kt`) is a reasonable approach for debug logging. It uses a sealed `when` without `else`, which is good. No concerns.

### 2. Reviewer `feedbackDir` on Iteration 1

The design decision to make `feedbackDir` non-nullable on `ReviewerRequest` and create an empty directory in the fixture is sound. The `collectFeedbackFiles` implementation already handles empty directories gracefully, so this is a behavioral no-op.

## Documentation Updates Needed

None. The spec already documents the sealed hierarchy, and the implementation matches it.
