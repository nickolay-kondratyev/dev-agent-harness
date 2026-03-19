# Exploration: SubPartStatus, SubPartStateTransition, PartResult

## Already Implemented
- `AgentSignal` + `DoneResult` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt`

## To Implement
| Type | Notes |
|------|-------|
| `SubPartStatus` enum | 4 values: NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED |
| `SubPartStateTransition` sealed class | 4 entries: Spawn, Complete, Fail, IterateContinue |
| `transitionTo()` validator | Extension on SubPartStatus, takes AgentSignal |
| `validateCanSpawn()` validator | Extension on SubPartStatus |
| `PartResult` sealed class | 4 entries: Completed, FailedWorkflow, FailedToConverge, AgentCrashed |

## Spec References
- `doc/schema/plan-and-current-state.md` — SubPartStatus (lines 59-68), SubPartStateTransition (lines 89-135), validators (lines 137-183)
- `doc/core/PartExecutor.md` — PartResult (lines 28-41), AgentSignal spec (lines 53-76)

## Package Decision
- Ticket says `com.glassthought.shepherd.core.state` (new package)
- Validators reference `AgentSignal` from `com.glassthought.shepherd.core.agent.facade`

## Code Patterns
- Use `@AnchorPoint` annotation (from `com.asgard.core.annotation.AnchorPoint`)
- No Jackson annotations needed — enum values match JSON values naturally
- Tests use `AsgardDescribeSpec` with BDD GIVEN/WHEN/THEN
- One assert per `it` block

## Transition Table (from spec)
```
NOT_STARTED → IN_PROGRESS    (Spawn — validateCanSpawn)
IN_PROGRESS → COMPLETED      (Complete — Done(COMPLETED/PASS))
IN_PROGRESS → FAILED         (Fail — FailWorkflow/Crashed)
IN_PROGRESS → IN_PROGRESS    (IterateContinue — Done(NEEDS_ITERATION))
```

COMPLETED and FAILED are terminal. SelfCompacted is transparent (error if received).
