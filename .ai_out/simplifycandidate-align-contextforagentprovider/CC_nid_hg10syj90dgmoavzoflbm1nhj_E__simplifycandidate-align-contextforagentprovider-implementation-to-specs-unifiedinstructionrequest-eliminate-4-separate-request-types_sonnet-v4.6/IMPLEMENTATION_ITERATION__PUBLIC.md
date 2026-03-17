# Implementation Iteration — RoleCatalogEntry Layering Fix

## Changes Made

### 1. Moved `RoleCatalogEntry` to top-level data model type

**Problem**: `UnifiedInstructionRequest.roleCatalogEntries` was typed as `List<InstructionSections.RoleCatalogEntry>`. This created a layering violation — the data model was depending on the rendering layer (`InstructionSections` is a static text assembly helper).

**Fix**: Extracted `data class RoleCatalogEntry(val name, val description, val descriptionLong)` out of `InstructionSections` object and added it as a top-level type in `ContextForAgentProvider.kt`, co-located with `UnifiedInstructionRequest` and `AgentRole`.

Files changed:
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` — added top-level `RoleCatalogEntry`, changed field type from `List<InstructionSections.RoleCatalogEntry>` to `List<RoleCatalogEntry>`
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSections.kt` — removed nested `data class RoleCatalogEntry` (the `roleCatalog()` method continues to reference `RoleCatalogEntry` unqualified since they are in the same package)
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt` — updated from `InstructionSections.RoleCatalogEntry(...)` to `RoleCatalogEntry(...)` (same package, no import needed)

### 2. Added `partName` to debug log (Suggestion 2 — ACCEPTED)

`ContextForAgentProviderImpl.assembleInstructions` debug log now includes `partName` when non-null. Uses `buildList` with conditional `let` for clean optional inclusion.

File changed:
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` — debug log updated

### 3. Added `requireNotNull` validation guard tests (Suggestion 3 — ACCEPTED)

Four new test cases added to `ContextForAgentProviderAssemblyTest.kt`, one per role with a missing required field:
- DOER with `partName = null` → throws `IllegalArgumentException`
- REVIEWER with `partName = null` → throws `IllegalArgumentException`
- PLANNER with `planJsonOutputPath = null` → throws `IllegalArgumentException`
- PLAN_REVIEWER with `planJsonContent = null` → throws `IllegalArgumentException`

These tests are trivial to add (each is a 7-line describe/it block using the existing fixture methods + `.copy()`), and they document important new behavior that would otherwise be invisible from the test suite.

## Reviewer Suggestion Decisions

| Suggestion | Decision | Rationale |
|---|---|---|
| S2: Add `partName` to debug log | ACCEPTED | Simple improvement (3 lines using `buildList` + conditional `let`). Aids diagnosability for execution agent instruction assembly. |
| S3: Add tests for `requireNotNull` guards | ACCEPTED | The guards are new behavior. Tests are trivial — 4 describe/it blocks using existing fixtures with `.copy()`. Each test is one assert per the standard. Low cost, clear value. |

## Test Results

```
BUILD SUCCESSFUL in 5s
5 actionable tasks: 4 executed, 1 up-to-date
```

All tests pass.
