# Implementation: Align ContextForAgentProvider to Spec — UnifiedInstructionRequest

## Summary

Replaced 4 separate request types + 4 interface methods with a single `UnifiedInstructionRequest`
data class and a single `assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path`
method, exactly as defined in `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E).

This is a pure structural refactor — no behavior changed. The same sections are assembled in the
same order with the same content. The `ContextForAgentProviderImpl` now dispatches on `AgentRole`
via a `when(role)` expression to select the correct internal section builder.

---

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` | Removed 4 old request classes (`DoerInstructionRequest`, `ReviewerInstructionRequest`, `PlannerInstructionRequest`, `PlanReviewerInstructionRequest`). Added `AgentRole` enum and `UnifiedInstructionRequest` data class. Replaced 4 interface methods with single `assembleInstructions(role, request)`. |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` | Replaced 4 `override suspend fun assemble*()` methods with single `override suspend fun assembleInstructions(role, request)`. Dispatches via `when(role)` to private `build*Sections(request: UnifiedInstructionRequest)` methods. Added `requireNotNull` guards for role-specific required fields. |
| `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt` | Updated all 4 factory methods to return `UnifiedInstructionRequest` instead of role-specific types. |
| `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt` | Updated all calls from `provider.assembleDoerInstructions(request)` / `provider.assembleReviewerInstructions(request)` to `provider.assembleInstructions(AgentRole.DOER, request)` / `provider.assembleInstructions(AgentRole.REVIEWER, request)`. |
| `app/src/test/kotlin/com/glassthought/shepherd/core/context/ExecutionAgentInstructionsKeywordTest.kt` | Same call-site updates + `request.partName!!` for the now-nullable field access. |
| `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlannerInstructionsKeywordTest.kt` | Updated call to `provider.assembleInstructions(AgentRole.PLANNER, request)`. Used `!!` for nullable `planJsonOutputPath` and `planMdOutputPath` accesses (known non-null in the fixture). |
| `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlanReviewerInstructionsKeywordTest.kt` | Updated call to `provider.assembleInstructions(AgentRole.PLAN_REVIEWER, request)`. Used `!!` for nullable `planJsonContent` access (known non-null in the fixture). |

---

## Test Results

**BUILD SUCCESSFUL** — all tests pass.

---

## Notable Decisions

### `requireNotNull` for role-specific required fields
Per the task spec, role-specific fields that are required for a given role use `requireNotNull(...)`
with a descriptive error message rather than `!!`. This gives a clear error if a caller mistakenly
omits a required field for a given role.

### `!!` in test files
Test fixture factory methods set role-specific fields to non-null values. Using `!!` in test code
for fields that the test itself just set is appropriate — it documents "we know this is non-null here"
without adding unnecessary nullable handling complexity to the test assertions.

### `planJsonOutputPath` / `planMdOutputPath` nullable in spec
The spec defines these as `Path? = null` in `UnifiedInstructionRequest`, whereas the old
`PlannerInstructionRequest` had them as non-null `Path`. The new impl uses `requireNotNull` to
enforce these are present when `role == PLANNER`, maintaining the same runtime guarantee with
a clearer error message.

### No new files created
`AgentRole` is defined in `ContextForAgentProvider.kt` (same file as the interface and
`UnifiedInstructionRequest`), keeping related types co-located per the existing pattern.
