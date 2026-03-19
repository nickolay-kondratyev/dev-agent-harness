# Exploration Summary: Self-Compacted Signal

## Already Exists
| Component | File | Status |
|-----------|------|--------|
| `ProtocolVocabulary.Signal.SELF_COMPACTED = "self-compacted"` | `core/context/ProtocolVocabulary.kt:75` | ✅ Complete |
| `AgentSignal.SelfCompacted` sealed variant | `core/agent/facade/AgentSignal.kt:52` | ✅ Complete |
| Callback script `self-compacted` action | `resources/scripts/callback_shepherd.signal.sh:124-126` | ✅ Complete |
| `SelfCompactionInstructionBuilder` | `core/compaction/SelfCompactionInstructionBuilder.kt` | ✅ Complete |
| PartExecutor recognizes SelfCompacted (errors as unexpected) | `core/executor/PartExecutorImpl.kt:81,152,183` | ✅ Correct for V1 |
| FakeAgentFacade supports SelfCompacted | `test/.../FakeAgentFacade.kt` | ✅ Complete |
| SubPartStateTransition errors on SelfCompacted (correct) | `core/state/SubPartStateTransition.kt:85` | ✅ Complete |

## What's Missing: Signal Dispatch (HTTP Server → AgentSignal)
- **No HTTP server exists yet** — no Ktor routing, no endpoint handlers
- **No string→AgentSignal mapping function** — when server arrives, it needs to map:
  - `"/callback-shepherd/signal/done"` + payload → `AgentSignal.Done(result)`
  - `"/callback-shepherd/signal/fail-workflow"` + payload → `AgentSignal.FailWorkflow(reason)`
  - `"/callback-shepherd/signal/self-compacted"` + payload → `AgentSignal.SelfCompacted`
- The mapping logic needs to:
  1. Parse `handshakeGuid` from JSON body
  2. Look up session in `SessionsState`
  3. Complete `signalDeferred` with correct `AgentSignal` variant
  4. Update `lastActivityTimestamp`

## Key Architecture Points
- `SessionEntry.signalDeferred: CompletableDeferred<AgentSignal>` — awaited by executor
- `SessionEntry.lastActivityTimestamp: Instant` — updated on every callback
- `SessionsState.lookup(guid)` — finds session by HandshakeGuid
- Signal dispatch pattern: HTTP callback → lookup SessionEntry → complete deferred → update timestamp
