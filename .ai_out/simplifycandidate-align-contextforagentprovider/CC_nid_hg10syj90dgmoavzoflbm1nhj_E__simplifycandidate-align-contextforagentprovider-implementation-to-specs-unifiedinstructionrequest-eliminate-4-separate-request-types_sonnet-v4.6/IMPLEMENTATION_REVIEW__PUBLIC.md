# IMPLEMENTATION REVIEW

**VERDICT: NEEDS_ITERATION**

---

## Summary

The implementation correctly replaces 4 interface methods + 4 request types with a single `assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path`. The `when(role)` dispatch is exhaustive (no `else`), `requireNotNull` guards are correctly placed for all role-specific required fields, and all tests pass. The refactor is structurally sound and is a pure behavior-preserving change.

One IMPORTANT issue prevents a clean PASS.

---

## IMPORTANT Issues

### 1. `UnifiedInstructionRequest.roleCatalogEntries` typed as `InstructionSections.RoleCatalogEntry` — spec deviation and layering violation

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` line 72

**Problem:**
The spec (`doc/core/ContextForAgentProvider.md` line 66) defines:
```kotlin
val roleCatalogEntries: List<RoleCatalogEntry> = emptyList(),
```

The implementation has:
```kotlin
val roleCatalogEntries: List<InstructionSections.RoleCatalogEntry> = emptyList(),
```

`InstructionSections` is a static text rendering helper (the "how to render" layer). `UnifiedInstructionRequest` is a data model (the "what data" layer). Having the data model depend on the rendering layer inverts the dependency — the data class should be independent of how it is rendered.

The spec clearly intends `RoleCatalogEntry` to be a standalone top-level type co-located with `UnifiedInstructionRequest` in `ContextForAgentProvider.kt`. This is also the ideal moment to fix this (the data class is being refactored specifically to match the spec).

**Fix:** Move `data class RoleCatalogEntry(...)` out of the `InstructionSections` object into `ContextForAgentProvider.kt` as a top-level type (next to `UnifiedInstructionRequest`). Update `InstructionSections.roleCatalog()` to accept `List<RoleCatalogEntry>` directly (it is in the same package, so no import change needed beyond removing the qualifier).

Note: the pre-existing `PlannerInstructionRequest` also used `InstructionSections.RoleCatalogEntry`, so this coupling was carried forward from before. The right fix is to make the break now.

---

## Suggestions

### 2. Debug log loses `partName` for execution agents

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` lines 32-37

The old per-role debug calls logged `partName` for DOER and REVIEWER. The new unified call only logs `role.name` and `iterationNumber`. For execution agent debugging (e.g., diagnosing which sub-part got which instructions), `partName` is a valuable correlating field.

Suggested fix:
```kotlin
out.debug("assembling_instructions") {
    buildList {
        add(Val(role.name, ValType.STRING_USER_AGNOSTIC))
        add(Val(request.iterationNumber.toString(), ValType.STRING_USER_AGNOSTIC))
        request.partName?.let { add(Val(it, ValType.STRING_USER_AGNOSTIC)) }
    }
}
```

### 3. No tests for `requireNotNull` validation guards

The `requireNotNull` checks are new behavior (e.g., calling `assembleInstructions(AgentRole.DOER, request)` with `partName = null` now throws `IllegalArgumentException`). These paths currently have no test coverage. Consider adding tests like:

```kotlin
describe("GIVEN a DOER request with null partName") {
    it("THEN assembleInstructions throws with a clear message") {
        shouldThrow<IllegalArgumentException> {
            provider.assembleInstructions(AgentRole.DOER, UnifiedInstructionRequest(
                roleDefinition = ..., ticketContent = "x", iterationNumber = 1,
                outputDir = tempDir, publicMdOutputPath = tempDir.resolve("PUBLIC.md"),
                partName = null, partDescription = null,
            ))
        }
    }
}
```

---

## What Passed Well

- Interface exactly matches the spec: `assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path`.
- `AgentRole` enum matches spec exactly: `DOER, REVIEWER, PLANNER, PLAN_REVIEWER`.
- `when(role)` is exhaustive — no `else` branch. Compiler-enforced.
- `UnifiedInstructionRequest` field layout and comments are an exact match to the spec (field names, nullability, defaults, grouping comments) — apart from the `RoleCatalogEntry` issue noted above.
- All 4 test files updated correctly. `!!` in tests is appropriately used for fixture-set fields.
- Pure structural refactor — verified by tracing the diff. No section assembly logic changed.
- All tests pass (`./test.sh`, `./sanity_check.sh`).
- Class-level documentation and AP reference preserved.
