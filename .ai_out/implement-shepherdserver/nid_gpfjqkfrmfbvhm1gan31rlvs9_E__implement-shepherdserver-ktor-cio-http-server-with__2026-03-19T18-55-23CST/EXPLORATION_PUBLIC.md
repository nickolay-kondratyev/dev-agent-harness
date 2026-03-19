# Exploration Summary: ShepherdServer Implementation

## Key Locations

| Item | Path |
|------|------|
| Spec | `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) |
| SessionsState | `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionsState.kt` (ap.7V6upjt21tOoCFXA7nqNh.E) |
| SessionEntry | `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` (ap.igClEuLMC0bn7mDrK41jQ.E) |
| AgentSignal | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt` (ap.uPdI6LlYO56c1kB5W0dpE.E) |
| SubPartRole | `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartRole.kt` |
| Constants | `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt` |
| HandshakeGuid | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt` |
| Server package | `app/src/main/kotlin/com/glassthought/shepherd/core/server/` |
| ShepherdValType | `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` |
| Test fixtures | `app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionTestFixtures.kt` |

## SessionEntry Fields Relevant to Server

- `signalDeferred: CompletableDeferred<AgentSignal>` — completed by lifecycle signals
- `lastActivityTimestamp: Instant` — updated on every callback
- `pendingPayloadAck: AtomicReference<PayloadId?>` — cleared by ack-payload
- `questionQueue: ConcurrentLinkedQueue<UserQuestionContext>` — appended by user-question
- `subPartIndex: Int` — 0=DOER, 1=REVIEWER (used for result validation)
- `role: SubPartRole` — derived from subPartIndex

## SessionsState Methods

- `suspend fun lookup(guid: HandshakeGuid): SessionEntry?`
- `suspend fun register(guid: HandshakeGuid, entry: SessionEntry)`
- `suspend fun removeAllForPart(partName: String): List<SessionEntry>`

## AgentSignal Variants

- `Done(result: DoneResult)` — COMPLETED, PASS, NEEDS_ITERATION
- `FailWorkflow(reason: String)`
- `Crashed(details: String)`
- `SelfCompacted` — data object

## Ktor Dependencies (already in build.gradle.kts)

```
implementation(libs.ktor.server.core)
implementation(libs.ktor.server.cio)
implementation(libs.ktor.server.content.negotiation)
implementation(libs.ktor.serialization.jackson)
```

Ktor version: 3.1.1. Need to add `ktor-server-tests` for testing.

## Existing Server Package

- `PayloadId.kt` — value class
- `AckedPayloadSender.kt` / `AckedPayloadSenderImpl.kt` — payload wrapping + ACK
- `PayloadAckTimeoutException.kt`
- **ShepherdServer.kt — NOT YET IMPLEMENTED**

## Constants.AGENT_COMM

Currently has `HANDSHAKE_GUID_ENV_VAR`. Need to add `SERVER_PORT_ENV_VAR`.
Also `REQUIRED_ENV_VARS.SERVER_PORT_ENV_VAR` already exists.

## Logging Pattern

```kotlin
private val out = outFactory.getOutForClass(MyClass::class)
out.info("message_key", Val(value, ShepherdValType.SPECIFIC_TYPE))
```

## 6 Endpoints to Implement

All under `POST /callback-shepherd/signal/{action}`:
1. `/signal/started` — side-channel, update timestamp
2. `/signal/done` — lifecycle, validate result vs role, complete signalDeferred
3. `/signal/user-question` — side-channel, append to questionQueue
4. `/signal/fail-workflow` — lifecycle, complete signalDeferred
5. `/signal/self-compacted` — lifecycle, complete signalDeferred
6. `/signal/ack-payload` — side-channel, clear pendingPayloadAck if matching
