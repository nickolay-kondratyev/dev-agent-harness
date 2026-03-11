## SetupPlanUseCase / ap.VLjh11HdzC8ZOhNCDOr2g.E

Routes to the appropriate planning strategy based on workflow type and returns a
`SetupPlanResult` (ref.ap.evYmpQfliHCHUTdK2QRgS.E).

### Routing

| Workflow type | Delegates to | Returns |
|---------------|-------------|---------|
| `straightforward` | `StraightforwardPlanUseCase` (ref.ap.6iySKY6iakspLNi3WenRO.E) | `SetupPlanResult.Ready(parts)` |
| `with-planning` | `DetailedPlanningUseCase` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) | `SetupPlanResult.NeedsPlanning(executor, convertFn)` |

This implementation is simple — it hands over complexity of generating the plan when needed
to `DetailedPlanningUseCase`.

---

### SetupPlanResult / ap.evYmpQfliHCHUTdK2QRgS.E

Sealed class returned by `setup()`. Tells `TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E)
whether planning is needed before execution can begin.

```kotlin
sealed class SetupPlanResult {
    /** Static parts from workflow JSON — no planning needed */
    data class Ready(val parts: List<Part>) : SetupPlanResult()

    /** Planning needed — caller runs planningExecutor, then calls convertPlan() */
    data class NeedsPlanning(
        val planningExecutor: PartExecutor,
        val convertPlanToExecutionParts: suspend () -> List<Part>,
    ) : SetupPlanResult()
}
```

- `Ready`: straightforward workflows — parts are already defined in the workflow JSON.
  `TicketShepherd` can proceed directly to execution.
- `NeedsPlanning`: `with-planning` workflows — the `planningExecutor` is a
  `DoerReviewerPartExecutor` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) for the
  PLANNER↔PLAN_REVIEWER loop. `TicketShepherd` runs `planningExecutor.execute()`, then
  calls `convertPlanToExecutionParts()` to get the execution parts from the approved plan.
