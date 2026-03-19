# Exploration: Health-Aware Await Loop in AgentFacadeImpl

## Current State
- `sendPayloadAndAwaitSignal` is a V1 stub (lines 120-148 of AgentFacadeImpl.kt)
- Sends raw instruction path via TMUX without ACK protocol
- Simple `freshSignalDeferred.await()` with no health monitoring

## Key Dependencies (all implemented)
| Component | File | Key Methods |
|---|---|---|
| `AgentUnresponsiveUseCase` | `usecase/healthmonitoring/AgentUnresponsiveUseCase.kt` | `handle(detectionContext, tmuxSession, diagnostics) → UnresponsiveHandleResult` |
| `AckedPayloadSender` | `core/server/AckedPayloadSender.kt` | `sendAndAwaitAck(tmuxSession, sessionEntry, payloadContent)` — throws `PayloadAckTimeoutException` |
| `QaDrainAndDeliverUseCase` | `core/question/QaDrainAndDeliverUseCase.kt` | `drainAndDeliver(sessionEntry, commInDir)` |
| `SessionEntry` | `core/session/SessionEntry.kt` | `lastActivityTimestamp: AtomicReference<Instant>`, `isQAPending`, `questionQueue` |
| `HarnessTimeoutConfig` | `core/data/HarnessTimeoutConfig.kt` | `healthTimeouts: HealthTimeoutLadder`, `healthCheckInterval` |

## Constructor Changes Needed
AgentFacadeImpl needs 3 new dependencies:
1. `ackedPayloadSender: AckedPayloadSender` — for ACK-wrapped payload delivery + ping
2. `agentUnresponsiveUseCase: AgentUnresponsiveUseCase` — for health decisions
3. `qaDrainAndDeliverUseCase: QaDrainAndDeliverUseCase` — for Q&A handling within the loop

## Health-Aware Loop Flow
1. Create fresh deferred + re-register session
2. Deliver payload via `AckedPayloadSender` (catches `PayloadAckTimeoutException` → Crashed)
3. Health monitoring loop:
   - Check `signalDeferred.isCompleted` → return signal
   - Check `isQAPending` → drain Q&A, skip health checks
   - Check `lastActivityTimestamp` staleness vs `normalActivity`
   - If stale → `AgentUnresponsiveUseCase(NO_ACTIVITY_TIMEOUT)` → PingSent
   - Wait `pingResponse` window → re-check `lastActivityTimestamp`
   - If still stale → `AgentUnresponsiveUseCase(PING_TIMEOUT)` → SessionKilled → return Crashed
4. Loop polls every `healthCheckInterval`

## commInDir
Derived from `payload.instructionFilePath.parent` (instructions live in `comm/in/`)
