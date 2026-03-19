# Implementation Review: InstructionSection + InstructionPlanAssembler

**Verdict: NEEDS_ITERATION**

## Summary

This PR introduces `InstructionSection` (sealed class with 7 subtypes) and `InstructionPlanAssembler` (rendering engine) as specified in the "Internal Design: Data-Driven Assembly" section of `doc/core/ContextForAgentProvider.md`. The implementation is clean, well-structured, and follows project conventions. However, there are two issues that need addressing before merge.

---

## IMPORTANT Issues

### 1. OutputPathSection rendering diverges from existing behavior (behavioral mismatch)

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` (lines 114-119)

The new `OutputPathSection` renders as:
```
## $label Output Path

Write your $label to:
`$path`
```

This does not match the **three existing output path formats** in `ContextForAgentProviderImpl`:

| Existing method | Heading | Body |
|---|---|---|
| `InstructionRenderers.publicMdOutputPath()` | `## Output Path` | `Write your PUBLIC.md to:` |
| `planJsonOutputPathSection()` | `## plan_flow.json Output Path` | `Write your plan_flow.json to:` |
| `planMdOutputPathSection()` | `## PLAN.md Output Path` | `Write your human-readable plan to:` |

Two specific mismatches:
- **PUBLIC.md**: existing heading is `## Output Path`, new rendering would produce `## PUBLIC.md Output Path`
- **PLAN.md**: existing body says `Write your human-readable plan to:`, new rendering would produce `Write your PLAN.md to:`

When the wiring ticket (nid_zseecydaikj0f2i2l14nwcfax_E) replaces the `buildList` approach with `assembleFromPlan`, the agent instructions will change. This may or may not be intentional. If intentional (simplifying to a uniform template), document it. If not, the rendering needs to match.

**Suggested fix:** If the intent is a uniform template, add a brief comment or note in the PR. If backward-compatible rendering is needed, consider adding an optional `bodyPrefix` parameter:
```kotlin
data class OutputPathSection(
    val label: String,
    val path: Path,
    val writeDescription: String = label,  // allows "human-readable plan" override
) : InstructionSection()
```

### 2. Test assertion contradicts its description (test lie)

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` (lines 268-270)

```kotlin
it("THEN does NOT include validate-plan query") {
    result shouldContain "Communicating with the Harness"
}
```

The test name says "does NOT include validate-plan query" but the assertion checks that the result **contains** "Communicating with the Harness" -- a positive check that does not verify the absence of `validate-plan`. This is a misleading test that provides a false sense of coverage for the negative case.

**Fix:**
```kotlin
it("THEN does NOT include validate-plan query") {
    result shouldNotContain "validate-plan"
}
```

---

## Suggestions

### 1. Avoid `result!!` in tests -- use `shouldNotBeNull()` chaining or extract once

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` (lines 101, 104, 124, 128, etc.)

Multiple `it` blocks use `result!!` after a sibling test already asserts non-null. While this works, `!!` in tests can produce confusing NPE stack traces instead of a clear assertion message. Consider extracting the non-null check into the describe scope:

```kotlin
describe("WHEN rendered") {
    val result = InstructionSection.PrivateMd.render(request)

    it("THEN returns non-null") {
        result.shouldNotBeNull()
    }

    it("THEN includes the Prior Session Context heading") {
        result.shouldNotBeNull() shouldContain "# Prior Session Context (PRIVATE.md)"
    }
}
```

This is a minor style point -- the tests work correctly as-is.

### 2. Temp directories not cleaned up

Test fixtures create `Files.createTempDirectory(...)` in `describe` blocks but never delete them. This is standard for short-lived test processes and not a real issue, but if the project conventions favor cleanup, consider using Kotest's `tempdir()` or an `afterSpec` block.

---

## What Passed

- **Sealed class design**: Clean, correct use of `data object` for stateless subtypes and `data class` for parameterized ones. Exhaustive `when` in `PartContext` -- no `else` branch, compiler-enforced.
- **Correct delegation to existing utilities**: `PartContext` delegates to `InstructionRenderers.partContext()`, `WritingGuidelines` returns `InstructionText.PUBLIC_MD_WRITING_GUIDELINES`, `CallbackHelp` delegates to `InstructionRenderers.callbackScriptUsage()`.
- **PrivateMd edge cases**: All four cases covered (null path, non-existent file, blank file, valid file). Logic matches `ContextForAgentProviderImpl.privateMdSection()` exactly.
- **RoleDefinition rendering**: Matches `ContextForAgentProviderImpl.roleDefinitionSection()` exactly.
- **Ticket rendering**: Matches `ContextForAgentProviderImpl.ticketSection()` exactly.
- **Assembler**: Correct null-filtering, separator insertion, file writing, logging. Constructor injection of `OutFactory` and `DispatcherProvider`.
- **Test coverage**: All 7 subtypes tested. Assembler tested for ordering, null-skipping, empty plan, and PartContext null for planner.
- **PartContext returns null for Planner/PlanReviewer**: Correctly tested.
- **CallbackHelp parameterization**: Both doer (completed) and reviewer+plan-validation (pass/needs_iteration) variants tested.
- **No existing tests removed**: Verified -- `ContextForAgentProviderImpl` and its tests are untouched.
- **All tests pass** (`./test.sh` and `sanity_check.sh`).
- **Detekt clean**.
- **SRP**: Sealed class holds rendering logic per section; assembler only walks/joins/writes. Clean separation.
- **Code follows project Kotlin standards**: Constructor injection, structured logging with `Val`/`ValType`, `OutFactory`, `DispatcherProvider`, no singletons, no `@Deprecated`.

---

## Documentation Updates Needed

None required for this PR. The spec (`doc/core/ContextForAgentProvider.md`) already documents the `InstructionSection` sealed class and `assembleFromPlan`. The anchor points are correctly assigned in both new files.
