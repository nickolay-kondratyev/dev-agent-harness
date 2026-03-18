# Detailed Plan: ContextForAgentProvider Sealed Redesign

## Problem Understanding

Replace the flat `UnifiedInstructionRequest + AgentRole` enum dispatch with a sealed `AgentInstructionRequest` hierarchy per spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). This moves role-field validation from runtime `requireNotNull` guards to compile-time type safety. Additionally, add `PrivateMd` section to all 4 role instruction plans.

**Constraints:**
- No callers outside `core/context` package -- fully self-contained change
- 7 files total: 2 production, 5 test (including fixtures)
- Zero risk of breaking other modules

**Assumptions:**
- `RoleCatalogEntry` stays as-is (unchanged)
- `InstructionText`, `InstructionRenderers`, `ProtocolVocabulary` stay as-is (unchanged)
- The `companion object` factory on the interface stays (just the method signature changes)

---

## Phase 1: Sealed Hierarchy in `ContextForAgentProvider.kt`

**Goal:** Replace `AgentRole` enum + `UnifiedInstructionRequest` with `ExecutionContext` + sealed `AgentInstructionRequest`.

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`

### Steps

1. **Delete** `enum class AgentRole` entirely.

2. **Delete** `data class UnifiedInstructionRequest` entirely.

3. **Add** `ExecutionContext` data class (composition for shared doer+reviewer fields):
   ```kotlin
   data class ExecutionContext(
       val partName: String,
       val partDescription: String,
       val planMdPath: Path?,               // null -> no-planning workflow
       val priorPublicMdPaths: List<Path>,
   )
   ```

4. **Add** sealed class `AgentInstructionRequest` with 4 subtypes exactly per spec:
   - Common abstract properties: `roleDefinition`, `ticketContent`, `iterationNumber`, `outputDir`, `publicMdOutputPath`
   - `DoerRequest`: common + `executionContext: ExecutionContext` + `reviewerPublicMdPath: Path?`
   - `ReviewerRequest`: common + `executionContext: ExecutionContext` + `doerPublicMdPath: Path` (non-nullable) + `feedbackDir: Path` (non-nullable)
   - `PlannerRequest`: common + `roleCatalogEntries: List<RoleCatalogEntry>` + `planReviewerPublicMdPath: Path?` + `planJsonOutputPath: Path` (non-nullable) + `planMdOutputPath: Path` (non-nullable)
   - `PlanReviewerRequest`: common + `planJsonContent: String` (non-nullable) + `planMdContent: String` (non-nullable) + `plannerPublicMdPath: Path` (non-nullable) + `priorPlanReviewerPublicMdPath: Path?`

5. **Update interface method** signature:
   ```kotlin
   suspend fun assembleInstructions(request: AgentInstructionRequest): Path
   ```
   Remove the `role: AgentRole` parameter -- the sealed type IS the discriminator.

6. **Keep** `RoleCatalogEntry` as-is.
7. **Keep** `companion object` factory, no change needed.

### Key Design Decisions
- `reviewerPublicMdPath` on `DoerRequest` stays nullable (semantically absent on iteration 1)
- `doerPublicMdPath` on `ReviewerRequest` is **non-nullable** -- reviewer always has doer output
- `feedbackDir` on `ReviewerRequest` is **non-nullable** -- the directory always exists (may be empty on iteration 1; the impl already handles empty dirs gracefully via `collectFeedbackFiles`)
- `planMdPath` in `ExecutionContext` stays nullable (absent in no-planning workflows)

---

## Phase 2: Update `ContextForAgentProviderImpl.kt`

**Goal:** Dispatch on sealed type instead of enum. Remove all `requireNotNull` guards. Add PrivateMd section.

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`

### Steps

1. **Update `assembleInstructions` signature** to match new interface (single `request: AgentInstructionRequest` param).

2. **Replace enum dispatch** with sealed `when`:
   ```kotlin
   val sections = when (request) {
       is AgentInstructionRequest.DoerRequest         -> buildDoerSections(request)
       is AgentInstructionRequest.ReviewerRequest     -> buildReviewerSections(request)
       is AgentInstructionRequest.PlannerRequest      -> buildPlannerSections(request)
       is AgentInstructionRequest.PlanReviewerRequest -> buildPlanReviewerSections(request)
   }
   ```
   No `else` branch -- compiler enforces exhaustiveness.

3. **Update each `build*Sections` method** parameter type from `UnifiedInstructionRequest` to the specific sealed subtype:
   - `buildDoerSections(request: AgentInstructionRequest.DoerRequest)`
   - `buildReviewerSections(request: AgentInstructionRequest.ReviewerRequest)`
   - `buildPlannerSections(request: AgentInstructionRequest.PlannerRequest)`
   - `buildPlanReviewerSections(request: AgentInstructionRequest.PlanReviewerRequest)`

4. **Remove all `requireNotNull` calls** -- the typed parameters guarantee presence at compile time:
   - `buildDoerSections`: remove `requireNotNull` for `partName`, `partDescription`. Access via `request.executionContext.partName` etc.
   - `buildReviewerSections`: remove `requireNotNull` for `partName`, `partDescription`. Access via `request.executionContext.*`. Access `request.doerPublicMdPath` directly (non-nullable). Access `request.feedbackDir` directly.
   - `buildPlannerSections`: remove `requireNotNull` for `planJsonOutputPath`, `planMdOutputPath`. Access directly.
   - `buildPlanReviewerSections`: remove `requireNotNull` for `planJsonContent`, `planMdContent`, `plannerPublicMdPath`. Access directly.

5. **Update field access** for execution context fields:
   - `request.partName` -> `request.executionContext.partName`
   - `request.partDescription` -> `request.executionContext.partDescription`
   - `request.planMdPath` -> `request.executionContext.planMdPath`
   - `request.priorPublicMdPaths` -> `request.executionContext.priorPublicMdPaths`

6. **Update reviewer feedback conditional** in `buildReviewerSections`:
   - Current: `if (request.iterationNumber > 1 && request.feedbackDir != null)`
   - New: `if (request.iterationNumber > 1)` (feedbackDir is always non-null; the impl already handles empty directories gracefully)

7. **Update debug logging** in `assembleInstructions`:
   - Remove `Val(role.name, ...)` -- derive role name from sealed type: `request::class.simpleName`
   - For partName: use `when` to extract from `DoerRequest`/`ReviewerRequest` execution context, or omit for planner/plan-reviewer.

8. **Add PrivateMd section** to all 4 role plans (insert after RoleDefinition, position #2 in all plans):

### PrivateMd Implementation

**Path convention:** `${outputDir.parent}/private/PRIVATE.md`
- `outputDir` is `${sub_part}/comm/in/` so `outputDir.parent` is `${sub_part}/comm/`
- Wait, that gives `comm/private/PRIVATE.md` which is wrong.
- Per spec: the path is `${sub_part}/private/PRIVATE.md`
- `outputDir` = `${sub_part}/comm/in`
- So: `outputDir.parent.parent.resolve("private/PRIVATE.md")` = `${sub_part}/private/PRIVATE.md`

**Add a private method:**
```kotlin
private fun privateMdSection(outputDir: Path): String? {
    // outputDir = .../sub_part/comm/in -> parent.parent = .../sub_part
    val privateMdPath = outputDir.parent.parent.resolve("private/PRIVATE.md")
    return if (Files.exists(privateMdPath)) {
        "# Prior Session Context (PRIVATE.md)\n\n${privateMdPath.readText()}"
    } else {
        null  // silently skip if absent
    }
}
```

**Insert into each build method** after role definition (position 2):
```kotlin
// 2. PrivateMd (silently skipped if absent)
privateMdSection(request.outputDir)?.let { add(it) }
```

---

## Phase 3: Update Test Fixtures (`ContextTestFixtures.kt`)

**Goal:** Update fixture factory methods to return sealed subtypes instead of `UnifiedInstructionRequest`.

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt`

### Steps

1. **`doerInstructionRequest(tempDir)`** -> returns `AgentInstructionRequest.DoerRequest`:
   ```kotlin
   fun doerInstructionRequest(tempDir: Path): AgentInstructionRequest.DoerRequest {
       // ... same setup ...
       return AgentInstructionRequest.DoerRequest(
           roleDefinition = roleDefinition("IMPLEMENTOR"),
           ticketContent = "...",
           iterationNumber = 1,
           outputDir = outputDir,
           publicMdOutputPath = publicMdOutputPath,
           executionContext = ExecutionContext(
               partName = "part_1_implementation",
               partDescription = "Implement the main feature",
               planMdPath = null,
               priorPublicMdPaths = emptyList(),
           ),
           reviewerPublicMdPath = null,
       )
   }
   ```

2. **`reviewerInstructionRequest(tempDir)`** -> returns `AgentInstructionRequest.ReviewerRequest`:
   - `doerPublicMdPath` is now non-nullable (already created in fixture)
   - `feedbackDir` is now non-nullable. Create an empty directory for iteration-1 fixtures:
     ```kotlin
     val feedbackDir = tempDir.resolve("doer/__feedback")
     Files.createDirectories(feedbackDir)
     ```

3. **`reviewerInstructionRequestWithFeedback(tempDir)`** -> returns `AgentInstructionRequest.ReviewerRequest`:
   - Same as above but with `iterationNumber = 2` and resource-based feedbackDir

4. **`plannerRequest(tempDir)`** -> returns `AgentInstructionRequest.PlannerRequest`:
   - `planJsonOutputPath` and `planMdOutputPath` are now non-nullable (already set in fixture)

5. **`planReviewerRequest(tempDir)`** -> returns `AgentInstructionRequest.PlanReviewerRequest`:
   - `planJsonContent`, `planMdContent`, `plannerPublicMdPath` are now non-nullable (already set in fixture)

### Important: `.copy()` compatibility
The existing tests use `.copy()` on `UnifiedInstructionRequest` to create variants. With sealed subtypes, `.copy()` works on the concrete data class, BUT the available fields change. Tests that do `.copy(partName = null)` will no longer compile since `partName` is inside `ExecutionContext`. This is **intentional** -- several of the `requireNotNull` guard tests become **obsolete** because the compiler prevents the invalid state.

---

## Phase 4: Update Test Files

### 4a. `ContextForAgentProviderAssemblyTest.kt`

**Changes:**

1. **Remove `AgentRole` from all `assembleInstructions` calls** -- no more `role` parameter:
   - `provider.assembleInstructions(AgentRole.DOER, request)` -> `provider.assembleInstructions(request)`
   - Same for REVIEWER, PLANNER, PLAN_REVIEWER

2. **Update `.copy()` calls** on doer/reviewer requests to work with `ExecutionContext`:
   - `baseRequest.copy(planMdPath = planMdPath)` -> `baseRequest.copy(executionContext = baseRequest.executionContext.copy(planMdPath = planMdPath))`
   - `baseRequest.copy(priorPublicMdPaths = listOf(...))` -> `baseRequest.copy(executionContext = baseRequest.executionContext.copy(priorPublicMdPaths = listOf(...)))`
   - `baseRequest.copy(iterationNumber = 2, reviewerPublicMdPath = ...)` stays as-is (these are top-level DoerRequest fields)

3. **Delete the 4 `requireNotNull` guard test blocks** -- they test runtime validation that is now compile-time:
   - "GIVEN a DOER request with null partName"
   - "GIVEN a REVIEWER request with null partName"
   - "GIVEN a PLANNER request with null planJsonOutputPath"
   - "GIVEN a PLAN_REVIEWER request with null planJsonContent"

   These tests literally cannot be written anymore -- the sealed types make these states unrepresentable. This is the **entire point** of the redesign.

4. **Update reviewer iteration-1 test**: The `.copy(partName = null)` test is deleted (above), but the existing iteration-1 reviewer tests still work -- just remove the `AgentRole` parameter.

### 4b. `ExecutionAgentInstructionsKeywordTest.kt`

1. Remove `AgentRole.DOER` and `AgentRole.REVIEWER` from `assembleInstructions` calls.
2. All `shouldContain` assertions remain unchanged -- the output content is identical.
3. Update `request.partName!!` -> `request.executionContext.partName` (it's non-nullable in ExecutionContext).

### 4c. `PlannerInstructionsKeywordTest.kt`

1. Remove `AgentRole.PLANNER` from `assembleInstructions` call.
2. Update `request.planJsonOutputPath!!` -> `request.planJsonOutputPath` (non-nullable in PlannerRequest).
3. Update `request.planMdOutputPath!!` -> `request.planMdOutputPath` (non-nullable in PlannerRequest).

### 4d. `PlanReviewerInstructionsKeywordTest.kt`

1. Remove `AgentRole.PLAN_REVIEWER` from `assembleInstructions` call.
2. Update `request.planJsonContent!!` -> `request.planJsonContent` (non-nullable in PlanReviewerRequest).

---

## Phase 5: Add PrivateMd Tests

**Goal:** Verify PrivateMd section is included when file exists, silently skipped when absent.

**File:** `ContextForAgentProviderAssemblyTest.kt` (add new describe blocks)

### Tests to Add

1. **"GIVEN a doer request with PRIVATE.md present"**
   - Create `${sub_part}/private/PRIVATE.md` with known content
   - Assemble instructions
   - THEN output contains PRIVATE.md content
   - THEN PRIVATE.md content appears after role definition section

2. **"GIVEN a doer request without PRIVATE.md"**
   - Do NOT create the private/PRIVATE.md file
   - Assemble instructions
   - THEN output does NOT contain "Prior Session Context" header

3. **One test per role** is sufficient to verify PrivateMd is wired into each plan. But since the mechanism is identical across all 4 roles (same `privateMdSection` method), testing with doer + one other role (e.g., planner) provides sufficient coverage without over-testing.

---

## Implementation Order (Recommended Sequence)

1. **Phase 1**: Define the sealed hierarchy in `ContextForAgentProvider.kt` -- this will cause compile errors everywhere (expected).
2. **Phase 3**: Update `ContextTestFixtures.kt` -- this resolves fixture compilation.
3. **Phase 2**: Update `ContextForAgentProviderImpl.kt` -- this resolves production compilation.
4. **Phase 4**: Update all 4 test files -- this resolves test compilation.
5. **Phase 5**: Add PrivateMd tests and implementation.
6. **Run `./test.sh`** to verify all tests pass.

Alternatively, phases 2 and 3 can be done together since they're both needed for compilation.

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Missed `.copy()` call in tests | Low | Compiler will catch -- all `.copy()` on deleted type will fail |
| `feedbackDir` non-nullable breaks reviewer iteration-1 fixture | Medium | Create empty dir in fixture (empty dir is already handled gracefully by `collectFeedbackFiles`) |
| PrivateMd path derivation wrong | Medium | Write test first, verify path convention matches spec: `outputDir.parent.parent.resolve("private/PRIVATE.md")` |
| Other modules reference `AgentRole` or `UnifiedInstructionRequest` | None | Grep confirmed zero external callers |

---

## Test Acceptance Criteria

1. All existing tests pass (with updated API) -- `./test.sh` green
2. Compile-time: constructing a `ReviewerRequest` without `doerPublicMdPath` is a build error
3. Compile-time: constructing a `PlannerRequest` without `planJsonOutputPath` is a build error
4. Compile-time: passing `partName` to a `PlannerRequest` is impossible (field doesn't exist)
5. No `requireNotNull` calls remain in `ContextForAgentProviderImpl` for role-specific fields
6. PrivateMd section present when file exists, absent when file doesn't exist
7. `AgentRole` enum no longer exists
8. `UnifiedInstructionRequest` no longer exists
9. Detekt passes (run via `./test.sh`)
