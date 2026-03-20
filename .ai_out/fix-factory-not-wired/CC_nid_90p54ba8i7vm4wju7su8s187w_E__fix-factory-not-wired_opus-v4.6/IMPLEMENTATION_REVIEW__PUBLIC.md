# Implementation Review: Wire SetupPlanUseCaseFactory

## Summary

The change replaces a blanket `TODO()` in the `SetupPlanUseCaseFactory` default parameter of `TicketShepherdCreatorImpl` with real wiring. This narrows the failure surface: straightforward workflows now work end-to-end (at the factory level), while detailed-planning workflows still throw `NotImplementedError` but with a precise message pointing at the missing `PlanningPartExecutorFactory` production impl.

**Only one production file was modified** (`TicketShepherdCreator.kt`). No test files were added, removed, or modified. All existing tests pass (both `./test.sh` and `./sanity_check.sh` exit 0).

**Overall assessment: APPROVE.** The change is correct, minimal, and well-scoped. One suggestion below.

## CRITICAL Issues

None.

## IMPORTANT Issues

None.

## Suggestions

### 1. `wd.parts ?: emptyList()` -- consider using `wd.parts!!` instead

At line 126 of `TicketShepherdCreator.kt`:

```kotlin
straightforwardPlanUseCase = StraightforwardPlanUseCaseImpl(
    parts = wd.parts ?: emptyList(),
    outFactory = of,
),
```

The `?: emptyList()` fallback is unreachable in practice because `SetupPlanUseCaseImpl` only invokes `straightforwardPlanUseCase.execute()` when `workflowDefinition.isStraightforward` is true, which means `parts != null`. And when `isWithPlanning` is true, `parts` is null but `StraightforwardPlanUseCaseImpl.execute()` is never called.

However, the `emptyList()` fallback silently produces a `StraightforwardPlanUseCaseImpl` with zero parts, which could mask a bug if the routing logic ever changes. Using `wd.parts!!` (or `wd.parts ?: error("...")`) would fail fast if the invariant is violated.

That said, this is a minor point -- the routing is validated by `WorkflowDefinition.init` and by `SetupPlanUseCaseImpl`'s `when` block. The current code is defensible. I leave it to the author's judgment.

## Correctness Analysis

1. **Does the fix resolve the original error for straightforward workflows?** Yes. The factory now constructs a real `SetupPlanUseCaseImpl` with a fully wired `StraightforwardPlanUseCaseImpl`. The `TODO()` is pushed to the `DetailedPlanningUseCase` code path only.

2. **Is the wiring correct?** Yes. The `SetupPlanUseCaseImpl` constructor takes `(workflowDefinition, straightforwardPlanUseCase, detailedPlanningUseCase, outFactory)` and the factory passes all four correctly.

3. **Is the `DetailedPlanningUseCase { TODO(...) }` valid?** Yes. `DetailedPlanningUseCase` is a `fun interface`, so the SAM lambda creates an instance whose `execute()` throws `NotImplementedError` with a descriptive message. This only triggers if a `with-planning` workflow is used.

4. **Is the remaining TODO in the right place?** Yes. It is scoped to exactly the code path that cannot be wired yet (`DetailedPlanningUseCase` needs `PlanningPartExecutorFactory`), rather than blocking all workflows.

5. **Any missing tests?** The existing test suite already exercises `TicketShepherdCreatorImpl` with stub/fake factories, so the default parameter wiring is not directly tested. This is acceptable because the default is only used in production, and the wiring correctness is verified by type-checking at compile time. An integration test covering the real production path would be valuable but is out of scope for this fix.

## Documentation Updates Needed

None.
