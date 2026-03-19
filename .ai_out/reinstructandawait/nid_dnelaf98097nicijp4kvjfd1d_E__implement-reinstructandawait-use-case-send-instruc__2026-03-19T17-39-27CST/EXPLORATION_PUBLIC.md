# Exploration: ReInstructAndAwait Use Case

## Spec Location
`doc/use-case/ReInstructAndAwait.md` (ap.QZYYZ2gTi1D2SQ5IYxOU6.E)

## Summary
Thin abstraction over `AgentFacade.sendPayloadAndAwaitSignal` that maps `AgentSignal` → `ReInstructOutcome` sealed class. Eliminates ~15-20 lines of duplicated plumbing at 3+ call sites.

## Key Files
| Component | Path |
|-----------|------|
| **Spec** | `doc/use-case/ReInstructAndAwait.md` |
| **AgentSignal** | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt` |
| **AgentFacade** | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt` |
| **AgentPayload** | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentPayload.kt` |
| **SpawnedAgentHandle** | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnedAgentHandle.kt` |
| **PartResult** | `app/src/main/kotlin/com/glassthought/shepherd/core/state/PartResult.kt` |
| **FakeAgentFacade** | `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt` |
| **Example UseCase** | `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCase.kt` |

## Interface (from spec)
```kotlin
sealed class ReInstructOutcome {
    data class Responded(val signal: AgentSignal.Done) : ReInstructOutcome()
    data class FailedWorkflow(val reason: String) : ReInstructOutcome()
    data class Crashed(val details: String) : ReInstructOutcome()
}

interface ReInstructAndAwait {
    suspend fun execute(handle: SpawnedAgentHandle, message: String): ReInstructOutcome
}
```

## Signal Mapping
| AgentSignal | ReInstructOutcome |
|-------------|-------------------|
| `Done` | `Responded(signal)` |
| `FailWorkflow` | `FailedWorkflow(reason)` |
| `Crashed` | `Crashed(details)` |
| `SelfCompacted` | Handled by facade transparently (never reaches this class) |

## Design Note: message: String vs AgentPayload
- Spec defines `execute(handle, message: String)`
- AgentFacade takes `AgentPayload(instructionFilePath: Path)`
- Impl must bridge: convert `message` (file path string) → `AgentPayload(Path.of(message))`

## Testing Pattern
- FakeAgentFacade: programmable via `onSendPayloadAndAwaitSignal` handler
- BDD with `AsgardDescribeSpec`, GIVEN/WHEN/THEN, one assert per `it` block
- Three core scenarios: Done → Responded, Crashed → Crashed, FailWorkflow → FailedWorkflow
