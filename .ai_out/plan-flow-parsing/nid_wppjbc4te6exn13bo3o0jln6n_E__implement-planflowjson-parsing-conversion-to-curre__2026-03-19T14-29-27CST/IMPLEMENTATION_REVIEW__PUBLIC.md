# Implementation Review: PlanFlowConverter

## Summary

`PlanFlowConverter` reads `plan_flow.json`, validates it, initializes runtime fields, appends execution parts to `CurrentState`, ensures directory structure, flushes to disk, and deletes the source file. The implementation is clean, well-structured, and follows the spec closely. All 26 tests pass. The conversion pipeline steps are correct and in the right order.

**Overall assessment: GOOD -- two important issues, one suggestion.**

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `sessionIds` not cleared during initialization -- stale session records leak through

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentStateInitializer.kt` (lines 48-53)

`CurrentStateInitializerImpl.initializeSubPart()` sets `status = NOT_STARTED` and `iteration.current = 0`, but does **not** clear `sessionIds`. If a `plan_flow.json` contains stale `sessionIds` (as exercised in the "runtime fields already present" test), those session records survive initialization and end up in the in-memory `CurrentState`.

The test at line 283 correctly verifies that `status` is re-initialized to `NOT_STARTED`, but there is no test verifying `sessionIds` is cleared. Since `sessionIds` are runtime-only fields (spec says "Absent" in plan_flow.json, "Present" in CurrentState), the initializer should null them out.

**Fix in `CurrentStateInitializerImpl.initializeSubPart()`:**
```kotlin
private fun initializeSubPart(subPart: SubPart): SubPart {
    return subPart.copy(
        status = SubPartStatus.NOT_STARTED,
        iteration = subPart.iteration?.copy(current = 0),
        sessionIds = null,  // Clear any stale session records from plan_flow.json
    )
}
```

**Add test in `PlanFlowConverterTest`:**
```kotlin
it("THEN sessionIds are cleared (null)") {
    val ctx = createTestContext()
    ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
    val currentState = CurrentState(parts = mutableListOf())
    val result = ctx.converter.convertAndAppend(currentState)
    result[0].subParts[0].sessionIds shouldBe null
}
```

Note: This fix is in `CurrentStateInitializerImpl`, not `PlanFlowConverter` itself -- which is the correct place since `initializePart()` is the single point responsible for runtime field initialization.

### 2. `PlanConversionException` extends `RuntimeException` instead of `AsgardBaseException`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt`

The implementation summary acknowledges this: "PlanConversionException extends RuntimeException (not AsgardBaseException) because AsgardBaseException is not available in the current asgardCore jar."

The CLAUDE.md Kotlin standards say: "Extend AsgardBaseException hierarchy for structured exceptions." If `AsgardBaseException` is genuinely unavailable in the current asgardCore 1.0.0, this is acceptable as a temporary deviation -- but it should have a follow-up ticket to migrate when the dependency is updated. The comment in the implementation summary is good documentation of the decision.

**Action:** Create a follow-up ticket to migrate `PlanConversionException` to `AsgardBaseException` when available. Add a code comment at the exception class referencing the ticket.

---

## Suggestions

### 1. Missing test: `plan_flow.json` does not exist

There is no test for the case where `plan_flow.json` is missing from disk when `convertAndAppend()` is called. Currently this would throw a `NoSuchFileException` from `readText()`, which is a raw JVM exception -- not a `PlanConversionException`. Callers expecting `PlanConversionException` would not catch it.

Consider either:
- (a) Wrapping the `readText()` call in a try-catch to produce a `PlanConversionException` with a clear message, or
- (b) Adding a pre-check `require(planFlowPath.exists())` with a descriptive message

And adding a corresponding test.

### 2. Test DRY -- repeated setup in every `it` block

Every `it` block in the test file repeats the same 4-line setup:
```kotlin
val ctx = createTestContext()
ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
val currentState = CurrentState(parts = mutableListOf(planningPart))
val result = ctx.converter.convertAndAppend(currentState)
```

This is 13 `it` blocks with near-identical setup. Per CLAUDE.md: "Use BDD style testing with GIVEN/WHEN/THEN to DRY up common test code." The BDD structure (describe blocks for GIVEN/WHEN) is present, but the actual DRY benefit is not realized because each `it` block re-executes the full setup.

Consider using a `lateinit` or lazy pattern at the `WHEN` describe level to share setup across `it` blocks, e.g.:

```kotlin
describe("WHEN convertAndAppend is called") {
    lateinit var ctx: PlanFlowTestContext
    lateinit var currentState: CurrentState
    lateinit var result: List<Part>

    beforeEach {
        ctx = createTestContext()
        ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
        currentState = CurrentState(parts = mutableListOf(planningPart))
        result = ctx.converter.convertAndAppend(currentState)
    }

    it("THEN returns one execution part") {
        result shouldHaveSize 1
    }
    // ... other assertions use shared result/currentState
}
```

This reduces duplication from ~52 setup lines to ~8, while keeping tests isolated via `beforeEach`. Note: `beforeEach` in Kotest `DescribeSpec` is a suspend context, so calling `convertAndAppend` there works.

### 3. Minor: Two assertions in one `it` block

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverterTest.kt` lines 148-164

Two `it` blocks have two assertions each:
- Line 153-154: `currentState.parts[0].name shouldBe "planning"` AND `currentState.parts[0].phase shouldBe Phase.PLANNING`
- Line 162-163: `currentState.parts[1].name shouldBe "ui_design"` AND `currentState.parts[1].phase shouldBe Phase.EXECUTION`

Per CLAUDE.md: "Each `it` block contains one logical assertion." These could be split, or you could argue name+phase together constitute "it is the original planning part" as one logical assertion. Borderline -- mentioning for awareness only.

---

## Documentation Updates Needed

None required. The anchor point `ap.bV7kMn3pQ9wRxYz2LfJ8s.E` is set on the interface. Implementation summary is thorough.

---

## What's Good

- Clean separation: interface + impl pattern, constructor injection, no DI framework.
- Correct reuse of `ShepherdObjectMapper` (spec requirement: "One parser handles everything").
- Correct reuse of `CurrentStateInitializerImpl.initializePart()` companion method rather than duplicating runtime field logic.
- Validation is fail-fast with descriptive error messages including offending part names.
- Proper structured logging with `Val`/`ValType`.
- Atomic delete happens **after** flush, so crash between flush and delete is safe (plan_flow.json still exists, idempotent re-conversion is possible).
- Test coverage is thorough: happy path, multi-part, runtime field overwrite, all three validation failure modes, filesystem side effects (delete, flush, directory creation).
