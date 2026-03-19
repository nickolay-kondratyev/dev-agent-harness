# Review: DetailedPlanningUseCase Implementation

## Summary

The implementation creates `DetailedPlanningUseCase` (interface + impl) and `PlanningPartExecutorFactory` to orchestrate the planning lifecycle. The code is clean, well-structured, follows project conventions (constructor injection, `OutFactory` logging, `AsgardBaseException`, BDD tests), and handles the happy path and failure paths correctly. Tests cover 12 cases across happy path, three executor failure types, conversion retry success, and retry budget exhaustion.

**Verdict: NEEDS_ITERATION** -- one BLOCKING spec compliance issue.

---

## BLOCKING Issues (must fix)

### 1. Validation errors are NOT injected as planner context on retry

**Spec** (`doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md`, lines 28-30):
> On `PlanConversionException`: logs WARN, **restarts the planning loop** with validation errors **injected as planner context**. Counts against the planning iteration budget.

**What the implementation does** (`app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCase.kt`, lines 79-96):
```kotlin
} catch (e: PlanConversionException) {
    remainingRetries--
    out.warn(
        "plan_conversion_failed_will_retry",
        Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
        Val(remainingRetries.toString(), ShepherdValType.ATTEMPT_NUMBER),
    )
    if (remainingRetries <= 0) {
        failedToExecutePlanUseCase.handleFailure(...)
    }
    continue  // <-- just loops back, creates a brand new executor with NO error context
}
```

The `PlanConversionException.message` (containing validation errors like "All parts must have phase='execution'") is logged but **never passed to the next planning executor**. The new executor created via `partExecutorFactory.create()` has no mechanism to receive prior conversion errors. The `PlannerRequest` data class in `AgentInstructionRequest` (line 96-107 of `ContextForAgentProvider.kt`) has no field for prior conversion validation errors.

**Why this matters**: Without injecting the errors, the planner will likely produce the same invalid plan again, wasting all retry attempts. The whole point of the retry mechanism is that the planner gets feedback about what was wrong.

**Suggested fix**:
1. Add an optional `priorConversionErrors: String?` field to `PlannerRequest` (or to `PlanningPartExecutorFactory.create()` so the factory can thread it through to the executor's context provider).
2. Pass `e.message` from the caught `PlanConversionException` into the factory on retry.
3. Add a test that verifies the conversion error text is accessible to the planner on the retry attempt.

---

## IMPORTANT Suggestions (should fix)

### 2. `maxConversionRetries` is a separate budget from the spec's "planning iteration budget"

**Spec**: "Counts against the planning iteration budget."

**Implementation**: Uses a separate `maxConversionRetries` constructor parameter, completely decoupled from the `PartExecutorImpl`'s `IterationConfig.max`.

The implementation PUBLIC.md acknowledges this as intentional. However, this means a planning run could theoretically consume `IterationConfig.max` iterations inside the executor AND THEN `maxConversionRetries` additional full planning runs -- potentially much more work than the spec intends. The spec says conversion retries count against the SAME budget.

This may be an acceptable deviation if the team prefers it, but it should be explicitly called out as a spec deviation and the spec should be updated to match, or the implementation should be adjusted.

### 3. Test duplication in setup

Each `it` block in the test file repeats the full setup (create factory, create executor, create converter, create use case). This is 8-12 lines of identical boilerplate per test. The BDD style with `describe` blocks is meant to DRY this up -- the setup should happen in the `describe` block and be shared by the `it` blocks within it.

For example, lines 134-168 have three `it` blocks all with identical setup:
```kotlin
val factory = FakePlanningPartExecutorFactory()
factory.onCreate(FakePartExecutor(PartResult.Completed))
val converter = FakePlanFlowConverter()
converter.onConvertAndAppend { sampleExecutionParts }
```

**Suggested fix**: Move shared setup to the enclosing `describe` block using `lateinit var` or similar pattern. This reduces noise and makes the tests more focused on what each `it` actually verifies.

---

## MINOR Observations (nice to have)

### 4. Session cleanup comment is accurate

The KDoc correctly notes that `PartExecutorImpl` kills sessions internally on all terminal paths (Completed, FailedWorkflow, FailedToConverge, AgentCrashed). Confirmed by reading `PartExecutorImpl` -- `killAllSessions()` is called on every terminal branch. No separate cleanup needed in `DetailedPlanningUseCaseImpl`.

### 5. Factory creates fresh executor per retry -- correct per spec

Each retry iteration calls `partExecutorFactory.create()` to get a fresh `PartExecutor`. This correctly "restarts the planning loop" as the spec requires. The test at line 288 verifies `factory.createCalls shouldHaveSize 2` for a single retry scenario.

### 6. `PlanConversionException` extends `AsgardBaseException` -- correct

File: `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt`. Follows project convention.

### 7. `PlanningPartExecutorFactory` as a fun interface -- clean

Keeps `DetailedPlanningUseCaseImpl` testable with a fake factory. Good application of DIP.

---

## Verdict: NEEDS_ITERATION

The BLOCKING issue (#1) must be addressed before this can be merged. The validation error injection is a core part of the spec's retry mechanism -- without it, retries are blind and unlikely to produce a different result.
