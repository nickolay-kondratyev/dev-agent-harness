# Gate 1 Complete — AgentFacade Interface Definitions

## Summary
Defined all interfaces and data classes for the AgentFacade testability seam. No implementations — Gate 1 scope is interface/type definitions only.

## Files Created

All in `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/`:

| File | Type | Anchor Point | Description |
|------|------|-------------|-------------|
| `AgentFacade.kt` | Interface | ap.1aEIkOGUeTijwvrACf3Ga.E | 4 suspend methods: spawnAgent, sendPayloadAndAwaitSignal, readContextWindowState, killSession |
| `AgentSignal.kt` | Sealed class + enum | ap.uPdI6LlYO56c1kB5W0dpE.E | Done(DoneResult), FailWorkflow, Crashed, SelfCompacted + DoneResult enum |
| `SpawnedAgentHandle.kt` | Data class | ap.kWchUPtTLqu73qXLHbKMs.E | guid: HandshakeGuid, sessionId: ResumableAgentSessionId, @Volatile lastActivityTimestamp |
| `SpawnAgentConfig.kt` | Data class | ap.nDDZyl11vax5mqhyAiiDr.E | partName, subPartName, subPartIndex, agentType, model, role, systemPromptPath, bootstrapMessage |
| `AgentPayload.kt` | Data class | ap.dPr77qbXaTmUgH0R3OBq0.E | instructionFilePath: Path |
| `ContextWindowState.kt` | Data class | ap.f4OVHiR0b7dpozBJDmIhv.E | remainingPercentage: Int? (null = stale/unknown) |

## Existing Types Reused (not duplicated)
- `HandshakeGuid` from `core.agent.sessionresolver`
- `ResumableAgentSessionId` from `core.agent.sessionresolver`
- `AgentType` from `core.data`

## Key Decisions
1. **`@Volatile var` for lastActivityTimestamp** — kotlinx.atomicfu not in project deps; @Volatile sufficient for single-writer pattern.
2. **Q&A handling documented as internal** — `sendPayloadAndAwaitSignal` KDoc explicitly notes that UserQuestionHandler is handled transparently within the method.
3. **`readContextWindowState` returns nullable percentage** — KDoc documents null means stale/unknown.

## Verification
- `./gradlew :app:build` passes (exit code 0).
- No behavioral tests needed — interfaces and data classes only.

## Next Steps (Gate 2)
- Implement `FakeAgentFacade` (programmable test double)
- Implement `TestClock` for virtual time
- Add `kotlinx-coroutines-test` dependency
- Proof-of-concept test using fake + virtual time
