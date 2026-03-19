# Implementation Review: SetupPlanUseCase & StraightforwardPlanUseCase

## Verdict: PASS

Overall this is a clean, well-structured implementation that follows the established `DetailedPlanningUseCase` pattern faithfully. Tests pass, spec compliance is solid, and the code is simple enough that there is little room for bugs.

---

## Summary

Two new use cases were added:

1. **SetupPlanUseCase** -- routes to either `StraightforwardPlanUseCase` or `DetailedPlanningUseCase` based on `WorkflowDefinition` properties.
2. **StraightforwardPlanUseCase** -- returns static `List<Part>` from `WorkflowDefinition.parts`.

Both follow the established pattern: `fun interface` + `Impl` class with constructor injection and structured `Out` logging.

---

## Issues

### No CRITICAL Issues

No security, correctness, or data-loss concerns.

### No IMPORTANT Issues

Architecture, spec compliance, and pattern consistency are all solid.

---

## Suggestions

### 1. DRY: Test setup duplication in `SetupPlanUseCaseImplTest`

**File:** `app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/SetupPlanUseCaseImplTest.kt`

Every single `it` block (all 8 of them) constructs the same 4-line `SetupPlanUseCaseImpl` with identical fakes. The `DetailedPlanningUseCaseImplTest` uses `lateinit` + `beforeEach` for this pattern (see lines 258-273 in that test file). The same approach would reduce ~50 lines of boilerplate here.

Consider extracting to `beforeEach` within each `describe("WHEN setup is called")` block:

```kotlin
describe("GIVEN a straightforward workflow") {
    describe("WHEN setup is called") {
        lateinit var straightforward: FakeStraightforwardPlanUseCase
        lateinit var detailed: FakeDetailedPlanningUseCase
        lateinit var useCase: SetupPlanUseCaseImpl

        beforeEach {
            straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
            detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
            useCase = SetupPlanUseCaseImpl(
                workflowDefinition = buildStraightforwardWorkflow(),
                straightforwardPlanUseCase = straightforward,
                detailedPlanningUseCase = detailed,
                outFactory = outFactory,
            )
        }

        it("THEN routes to StraightforwardPlanUseCase") {
            useCase.setup()
            straightforward.executeCalled shouldBe true
        }
        // ...etc
    }
}
```

**Severity:** Non-blocking suggestion. The current code is correct; this is purely a maintainability improvement.

### 2. `!!` in `StraightforwardPlanUseCaseImpl.execute()` -- safe but worth a comment

**File:** `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/StraightforwardPlanUseCase.kt`, line 30

```kotlin
return workflowDefinition.parts!!
```

This is safe because `SetupPlanUseCaseImpl` only routes here when `workflowDefinition.isStraightforward` is true (meaning `parts != null`). The `WorkflowDefinition.init` block also enforces mutual exclusivity. However, there is no compile-time guarantee that `StraightforwardPlanUseCaseImpl` will only ever be constructed with a straightforward workflow.

A defensive alternative would be to accept `List<Part>` directly in the constructor rather than the full `WorkflowDefinition`, which would eliminate the `!!` entirely and make the precondition explicit at the type level:

```kotlin
class StraightforwardPlanUseCaseImpl(
    private val parts: List<Part>,
    private val outFactory: OutFactory,
) : StraightforwardPlanUseCase {
    override suspend fun execute(): List<Part> {
        out.info("returning_static_parts_from_straightforward_workflow")
        return parts
    }
}
```

The wiring code (wherever `StraightforwardPlanUseCaseImpl` is constructed) would pass `workflowDefinition.parts!!` there, and the `!!` would live at the wiring boundary where the workflow type is already known. This follows the principle of pushing validation to the edges.

**Severity:** Non-blocking suggestion. The current approach is correct and protected by runtime checks.

### 3. Not yet wired into the dependency graph

The new use cases are not yet referenced from any wiring code (e.g., `TicketShepherdCreator`). This is likely intentional -- wiring will happen when the caller (`TicketShepherd`) is implemented. Just confirming this is expected and not an oversight.

---

## Spec Compliance

| Spec requirement | Status |
|---|---|
| `SetupPlanUseCase` routes `straightforward` to `StraightforwardPlanUseCase` | Compliant |
| `SetupPlanUseCase` routes `with-planning` to `DetailedPlanningUseCase` | Compliant |
| Both paths return `List<Part>` directly (no `SetupPlanResult` sealed class) | Compliant |
| `StraightforwardPlanUseCase` returns predefined parts from workflow JSON | Compliant |
| Anchor points match spec | Compliant (`ap.VLjh11HdzC8ZOhNCDOr2g.E`, `ap.6iySKY6iakspLNi3WenRO.E`) |

## Pattern Consistency

| Pattern | Status |
|---|---|
| `fun interface` + `Impl` class | Matches `DetailedPlanningUseCase` |
| Constructor injection, no DI framework | Matches |
| `OutFactory` structured logging with snake_case messages | Matches |
| `@AnchorPoint` annotation on interface | Matches |
| Fakes in tests (not mocks) | Matches |
| BDD GIVEN/WHEN/THEN structure | Matches |
| One assert per `it` block | Matches |

## Test Coverage Assessment

- **SetupPlanUseCaseImplTest**: 8 tests covering both routing paths (delegation, non-delegation, return value, size). Thorough.
- **StraightforwardPlanUseCaseImplTest**: 4 tests covering multi-part and single-part scenarios. Adequate for the simplicity of the use case.
- No existing tests were removed or modified.

## Documentation Updates Needed

None required. Anchor points are properly placed and referenced.
