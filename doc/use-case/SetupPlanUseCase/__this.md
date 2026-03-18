#need-tickets
## SetupPlanUseCase / ap.VLjh11HdzC8ZOhNCDOr2g.E

Routes to the appropriate planning strategy based on workflow type and returns `List<Part>`.

```kotlin
suspend fun setup(): List<Part>
```

The caller receives execution-ready parts regardless of workflow mode — the two-phase
planning protocol (run executor, convert plan) is fully encapsulated within
`DetailedPlanningUseCase`.

### Routing

| Workflow type | Delegates to | Returns |
|---------------|-------------|---------|
| `straightforward` | `StraightforwardPlanUseCase` (ref.ap.6iySKY6iakspLNi3WenRO.E) | `List<Part>` — static parts from workflow JSON |
| `with-planning` | `DetailedPlanningUseCase` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) | `List<Part>` — runs planning loop internally, returns execution parts |

This implementation is simple — it delegates complexity of generating the plan (when needed)
to `DetailedPlanningUseCase`, which owns the full planning lifecycle.

### SetupPlanResult sealed class — REMOVED / ap.evYmpQfliHCHUTdK2QRgS.E

The `SetupPlanResult` sealed class (`Ready` / `NeedsPlanning`) has been eliminated.
Both workflow paths now return `List<Part>` directly, removing the two-phase protocol
that forced the caller to orchestrate planning executor execution and plan conversion
as separate steps.
