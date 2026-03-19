# PRIVATE: Implementation Notes — DetectionContext + AgentUnresponsiveUseCase

## Status: COMPLETE (review feedback addressed)

## Implementation Details

### Package
`com.glassthought.shepherd.usecase.healthmonitoring`

### Key Types

- `DetectionContext` — enum with 3 values
- `UnresponsiveHandleResult` — sealed class: `SessionKilled(signal: AgentSignal.Crashed)` | `PingSent`
- `SingleSessionKiller` — fun interface, testability seam for TmuxSessionManager.killSession
- `UnresponsiveDiagnostics` — data class with handshakeGuid, timeoutDuration, staleDuration
- `AgentUnresponsiveUseCase` — **fun interface** (changed from plain interface per review)
- `AgentUnresponsiveUseCaseImpl` — implementation with 2 deps: OutFactory, SingleSessionKiller

### Wiring Point (for future integration)
When wiring in `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E):
```kotlin
val agentUnresponsiveUseCase = AgentUnresponsiveUseCaseImpl(
    outFactory = outFactory,
    sessionKiller = tmuxSessionManager::killSession,
)
```

### Pending Follow-Up
- Replace `sendRawKeys("Enter")` ping with proper `AckedPayloadSender` when implemented (ref.ap.tbtBcVN2iCl1xfHJthllP.E)
- Integration with AgentFacadeImpl health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E)

### Test Fakes Created (in test file, not shared yet)
- `FakeSingleSessionKiller` — records killed sessions
- `SpyTmuxCommunicator` — records sendKeys/sendRawKeys calls via `SendKeysCall` data class
- `AlwaysExistsChecker` — stub for TmuxSession construction in tests
- `SendKeysCall` — data class replacing `Pair<String, String>` (per review feedback)

### Review Iteration Log
- **Iteration 1**: Replaced `Pair` with `SendKeysCall` data class; changed `AgentUnresponsiveUseCase` to `fun interface`. Tests pass.

If these fakes are needed in other tests, consider extracting to a shared test utilities location.
