# Exploration: Remove SpawnTmuxAgentSessionUseCase

## Files to Remove (from ticket)
1. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/useCase/SpawnTmuxAgentSessionUseCase.kt`
2. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/data/StartAgentRequest.kt`
3. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/data/PhaseType.kt`
4. `app/src/test/kotlin/com/glassthought/bucket/SpawnTmuxAgentSessionUseCaseIntegTest.kt`
5. Remove `spawnTmuxAgentSession` field from `UseCases` data class in `Initializer.kt`

## Dependency Chain (files using the removed classes)

The following files are NOT in the ticket's explicit removal list but depend on `StartAgentRequest`/`PhaseType`:
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/AgentStarterBundleFactory.kt` — interface with `StartAgentRequest` in method sig
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/AgentTypeChooser.kt` — interface with `StartAgentRequest` in method sig
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/ClaudeCodeAgentStarterBundleFactory.kt` — implementation
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/DefaultAgentTypeChooser.kt` — implementation
- `app/src/test/kotlin/com/glassthought/bucket/DefaultAgentTypeChooserTest.kt` — unit test
- `app/src/test/kotlin/com/glassthought/bucket/ClaudeCodeAgentStarterBundleFactoryTest.kt` — unit test

**These are exclusively supporting `SpawnTmuxAgentSessionUseCase` — they must also be removed for a clean compilable build.**

## UseCases Data Class (in Initializer.kt)

Current structure:
```kotlin
data class UseCases(
    val spawnTmuxAgentSession: SpawnTmuxAgentSessionUseCase,
)
```

After removing the only field:
- `UseCases` becomes empty
- `ShepherdContext` currently takes `useCases: UseCases` — if `UseCases` is empty/gone, `ShepherdContext` must be updated too
- `SharedContextDescribeSpec` integration test infrastructure also wires `UseCases`

**Recommendation: Remove `UseCases` entirely and update `ShepherdContext` to not take it. An empty data class is misleading.**

## Initializer.kt Lines Affected
- Import `SpawnTmuxAgentSessionUseCase` removal
- `data class UseCases` block removal
- Construction of `SpawnTmuxAgentSessionUseCase` + all factory/chooser dependencies removal
- `val useCases = UseCases(...)` removal
- `ShepherdContext(..., useCases = useCases)` update

## ShepherdContext Impact
`ShepherdContext` constructor needs `useCases: UseCases` removed if `UseCases` is removed.
Update `SharedContextIntegFactory` accordingly.

## Compilation Risk
- **HIGH** if only ticket-specified files are removed without addressing the dependency chain
- **LOW** if all orphaned classes/tests are also removed in one pass

## Test Pattern to Preserve
The `SharedContextDescribeSpec` base class and `isIntegTestEnabled()` pattern are CORRECT and should be preserved. The removed integration test demonstrates the right approach — a future rebuild should follow the same structure.
