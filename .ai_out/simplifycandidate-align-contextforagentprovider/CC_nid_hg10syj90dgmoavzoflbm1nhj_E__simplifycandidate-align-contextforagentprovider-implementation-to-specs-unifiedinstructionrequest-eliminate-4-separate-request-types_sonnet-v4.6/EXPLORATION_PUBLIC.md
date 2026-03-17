# EXPLORATION: ContextForAgentProvider Simplification

## Summary

The spec defines a unified interface but current implementation has 4 separate methods + 4 request types. 
This is a well-scoped refactor with no production callers — only test files.

---

## Spec (doc/core/ContextForAgentProvider.md)

**Target interface**:
```kotlin
enum class AgentRole { DOER, REVIEWER, PLANNER, PLAN_REVIEWER }

interface ContextForAgentProvider {
    suspend fun assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path
}
```

**UnifiedInstructionRequest** (from spec lines 44-76):
```kotlin
data class UnifiedInstructionRequest(
    // ── common (all roles) ──────────────────────────────────────────────────
    val roleDefinition: RoleDefinition,
    val ticketContent: String,
    val iterationNumber: Int,
    val outputDir: Path,
    val publicMdOutputPath: Path,

    // ── execution agents (DOER + REVIEWER) ──────────────────────────────────
    val partName: String? = null,
    val partDescription: String? = null,
    val planMdPath: Path? = null,
    val priorPublicMdPaths: List<Path> = emptyList(),

    // ── DOER-only ───────────────────────────────────────────────────────────
    val reviewerPublicMdPath: Path? = null,

    // ── REVIEWER-only ───────────────────────────────────────────────────────
    val doerPublicMdPath: Path? = null,
    val feedbackDir: Path? = null,

    // ── PLANNER-only ────────────────────────────────────────────────────────
    val roleCatalogEntries: List<RoleCatalogEntry> = emptyList(),
    val planReviewerPublicMdPath: Path? = null,
    val planJsonOutputPath: Path? = null,
    val planMdOutputPath: Path? = null,

    // ── PLAN_REVIEWER-only ──────────────────────────────────────────────────
    val planJsonContent: String? = null,
    val planMdContent: String? = null,
    val plannerPublicMdPath: Path? = null,
    val priorPlanReviewerPublicMdPath: Path? = null,
)
```

---

## Current Implementation

### Interface (ContextForAgentProvider.kt)
4 separate methods:
- `assembleDoerInstructions(DoerInstructionRequest): Path`
- `assembleReviewerInstructions(ReviewerInstructionRequest): Path`  
- `assemblePlannerInstructions(PlannerInstructionRequest): Path`
- `assemblePlanReviewerInstructions(PlanReviewerInstructionRequest): Path`

### Request Types Field Overlap

**Common (ALL 4)**: roleDefinition, ticketContent, iterationNumber, outputDir, publicMdOutputPath

**Shared (DOER + REVIEWER)**: partName, partDescription, planMdPath, priorPublicMdPaths

**DOER-only**: reviewerPublicMdPath
**REVIEWER-only**: doerPublicMdPath, feedbackDir
**PLANNER-only**: roleCatalogEntries, planReviewerPublicMdPath, planJsonOutputPath, planMdOutputPath
**PLAN_REVIEWER-only**: planJsonContent, planMdContent, plannerPublicMdPath, priorPlanReviewerPublicMdPath

---

## Callers (TESTS ONLY — no production callers)

| Test File | Methods Called |
|-----------|----------------|
| ContextForAgentProviderAssemblyTest.kt | assembleDoerInstructions (6x), assembleReviewerInstructions (2x) |
| ExecutionAgentInstructionsKeywordTest.kt | assembleDoerInstructions (1x), assembleReviewerInstructions (1x) |
| PlannerInstructionsKeywordTest.kt | assemblePlannerInstructions (1x) |
| PlanReviewerInstructionsKeywordTest.kt | assemblePlanReviewerInstructions (1x) |

**Test Fixtures**: `ContextTestFixtures.kt` — factory methods for each request type

---

## AgentRole
From spec: `enum class AgentRole { DOER, REVIEWER, PLANNER, PLAN_REVIEWER }`
(May not exist as a standalone file yet — impl may need to create it)

---

## Implementation Notes

### Key files to modify:
1. `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` — replace interface
2. `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` — consolidate to 1 dispatcher
3. All 4 test files (update callers + fixtures)
4. `ContextTestFixtures.kt` — update factory methods

### Strategy:
- Add `AgentRole` enum (or find existing one)
- Create `UnifiedInstructionRequest` matching spec exactly  
- Replace 4 methods with 1 `assembleInstructions(role, request)` in interface and impl
- Impl dispatches on `role` to pick the correct internal instruction plan builder
- Update all test callers
