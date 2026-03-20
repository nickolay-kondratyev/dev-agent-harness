# Implementation Review: Wire DetailedPlanningUseCaseImpl in TicketShepherdCreator

## Summary

This change removes the `TODO` stub in `TicketShepherdCreatorImpl` and wires a real
`DetailedPlanningUseCaseImpl` backed by a new `ProductionPlanningPartExecutorFactory`. The
`SetupPlanUseCaseFactory` interface was expanded from `(WorkflowDefinition, OutFactory)` to a
richer `SetupPlanUseCaseContext` to carry the additional dependencies needed for the planning
infra. Dependency ordering in `wireTicketShepherd` was corrected. Tests pass (1460 tests, detekt
clean).

**Overall assessment**: The change achieves its stated goal. The main concern is a significant DRY
violation and a subtle correctness issue around duplicate `FailedToExecutePlanUseCaseImpl`
instances.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Major DRY Violation: `ProductionPlanningPartExecutorFactory` duplicates `ProductionPartExecutorFactoryCreator`

**Files**:
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactory.kt` (lines 162-265)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/ProductionPartExecutorFactoryCreator.kt` (lines 109-207)

Three companion methods are nearly line-for-line identical across both files:

| Method | `ProductionPartExecutorFactoryCreator` | `ProductionPlanningPartExecutorFactory` |
|--------|---------------------------------------|----------------------------------------|
| `loadRoleDefinitions` | lines 109-118 | lines 162-174 |
| `buildAgentFacade` | lines 120-165 | lines 176-222 |
| `buildGitCommitStrategy` | lines 167-206 | lines 224-264 |

The only difference in `buildAgentFacade` is that the existing one takes `clock` from an instance
field vs a parameter. `buildGitCommitStrategy` is identical. `loadRoleDefinitions` differs only in
whether it uses an instance field or a parameter for `roleCatalogLoader`.

**Suggestion**: Extract a shared `InfraBuilder` (or `ExecutorInfraWiring`) utility class that both
`ProductionPartExecutorFactoryCreator` and `ProductionPlanningPartExecutorFactory` delegate to.
This would house `loadRoleDefinitions`, `buildAgentFacade`, and `buildGitCommitStrategy`. Both
callers would construct it with the minimal config differences (clock, envProvider) and call the
shared methods.

### 2. Duplicate `FailedToExecutePlanUseCaseImpl` Instances -- Potential Correctness Issue

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactory.kt` (lines 237-246)

Inside `buildGitCommitStrategy`, a **new** `FailedToExecutePlanUseCaseImpl` is constructed with
`DefaultConsoleOutput()` and `DefaultProcessExiter()`. Meanwhile, `wireTicketShepherd` in
`TicketShepherdCreatorImpl` constructs its **own** `FailedToExecutePlanUseCaseImpl` (line 251) with
the injected `consoleOutput` and `processExiter` (which in tests are fakes).

This means:
- **In production**: Two separate `FailedToExecutePlanUseCaseImpl` instances exist with slightly
  different `ConsoleOutput`/`ProcessExiter` instances (both default, so functionally equivalent
  but wasteful).
- **In tests**: The planning-phase git failure path would call `DefaultProcessExiter.exit()` (the
  real one) instead of the test fake, potentially killing the test process.

The same issue exists in `ProductionPartExecutorFactoryCreator` (pre-existing), but introducing it
again in a new file without addressing it compounds the problem.

**Suggestion**: Pass the `FailedToExecutePlanUseCase` (already available in
`SetupPlanUseCaseContext.failedToExecutePlanUseCase`) through to
`ProductionPlanningPartExecutorFactory.create()` instead of constructing a new one internally.
This aligns the planning phase's git error handling with the top-level shepherd's error handling.

### 3. Unused Imports

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactory.kt` (lines 3-4, 9)

```kotlin
import com.asgard.core.data.value.Val         // unused
import com.asgard.core.data.value.ValType      // unused
import com.glassthought.shepherd.core.agent.facade.AgentFacade  // unused (only in KDoc)
```

These should be removed. Detekt may not catch KDoc-only references.

---

## Suggestions

### 1. Test Coverage: Verify `wireDetailedPlanningUseCase` Fail-Fast Stub

The `wireDetailedPlanningUseCase` method returns a fail-fast stub when `planningParts` is null
(straightforward workflow). There is no unit test that verifies this stub throws when invoked.
Consider adding a test:

```kotlin
describe("GIVEN a straightforward workflow with no planningParts") {
    describe("WHEN wireDetailedPlanningUseCase is called and the result is invoked") {
        it("THEN throws IllegalStateException with bug message") {
            // ...
        }
    }
}
```

### 2. Test Coverage: Tests Only Assert Type, Not Behavior

The `ProductionPlanningPartExecutorFactoryTest` tests only verify `shouldBeInstanceOf<PartExecutorImpl>()`.
They do not verify that the returned executor has the correct doer/reviewer configuration (e.g.,
that a doer-only part produces an executor with `reviewerConfig == null`, or that a doer+reviewer
part produces an executor with a non-null `reviewerConfig`). Consider adding assertions on the
executor's properties.

### 3. `println` for Warning is Acceptable for V1 but Should Be Tracked

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactory.kt` (line 67)

The `println` usage for `priorConversionErrors` logging is documented as a V1 limitation. This is
acceptable given the non-suspend constraint of the `create()` method, but ensure a follow-up
ticket exists for V2 to wire errors into the planner's instruction context.

### 4. Consider Making `SetupPlanUseCaseFactory.create` Non-Suspend

The factory was changed from non-suspend to suspend to accommodate `wireDetailedPlanningUseCase`
(which calls `ProductionPlanningPartExecutorFactory.create` suspend factory). This is fine
architecturally, but note it changes the public interface contract. All existing callers (tests)
were updated. No issue, just flagging the scope of the interface change.

---

## Documentation Updates Needed

None required. The CLAUDE.md spec references are unchanged. The `SetupPlanUseCaseFactory` KDoc was
updated appropriately.

---

## Acceptance Criteria Check

| Criterion | Status | Notes |
|-----------|--------|-------|
| Production `PlanningPartExecutorFactory` impl created | PASS | `ProductionPlanningPartExecutorFactory` |
| `DetailedPlanningUseCaseImpl` wired in `setupPlanUseCaseFactory` | PASS | Via `wireDetailedPlanningUseCase(ctx)` |
| TODO stub removed | PASS | Replaced with real wiring |
| Unit tests for production impl | PARTIAL | Tests exist but only assert type, not behavioral properties |
| `with-planning` workflows reach planning phase | PASS | No more `TODO()` crash |
