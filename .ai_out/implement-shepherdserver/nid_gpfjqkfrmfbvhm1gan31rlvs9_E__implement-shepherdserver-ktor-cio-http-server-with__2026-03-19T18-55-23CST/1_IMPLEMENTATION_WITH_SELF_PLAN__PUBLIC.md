# ShepherdServer Implementation

## What Was Done

Implemented `ShepherdServer` -- a Ktor CIO HTTP server with Jackson content negotiation
that handles 6 agent-to-harness signal callbacks under `POST /callback-shepherd/signal/{action}`.

### New Files

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/server/ShepherdServer.kt` | Ktor CIO server with 6 signal endpoints, session lookup, result validation |
| `app/src/main/kotlin/com/glassthought/shepherd/core/server/SignalRequests.kt` | Request DTOs for all 6 signal endpoints |
| `app/src/test/kotlin/com/glassthought/shepherd/core/server/ShepherdServerTest.kt` | 21 BDD unit tests using Ktor testApplication |

### Modified Files

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt` | Added `SERVER_PORT_ENV_VAR` to `AGENT_COMM` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` | Added `HANDSHAKE_GUID`, `SIGNAL_ACTION`, `RESULT`, `REASON`, `ROLE` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt` | Added `Signal.STARTED` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` | Changed `lastActivityTimestamp` from `val Instant` to `val AtomicReference<Instant>` for mutability |
| `app/build.gradle.kts` | Added `testImplementation(libs.ktor.server.test.host)` |
| `gradle/libs.versions.toml` | Added `ktor-server-test-host` library entry |
| Test fixtures (3 files) | Updated to use `AtomicReference(Instant.now())` for `lastActivityTimestamp` |

### Signal Endpoints

| Endpoint | Type | Behavior |
|----------|------|----------|
| `/signal/started` | Side-channel | Updates `lastActivityTimestamp`, returns 200 |
| `/signal/done` | Lifecycle | Validates result vs role, completes `signalDeferred`, returns 200 |
| `/signal/user-question` | Side-channel | Appends to `questionQueue`, returns 200 |
| `/signal/fail-workflow` | Lifecycle | Completes `signalDeferred` with `FailWorkflow`, returns 200 |
| `/signal/self-compacted` | Lifecycle | Completes `signalDeferred` with `SelfCompacted`, returns 200 |
| `/signal/ack-payload` | Side-channel | Clears `pendingPayloadAck` if matching, returns 200 |

### Error Handling

- Unknown `handshakeGuid` -> 404 + WARN log
- Invalid result string -> 400 + WARN log
- Role-mismatched result -> 400 + WARN log
- Duplicate lifecycle signal -> 200 + WARN log (idempotent)
- Late fail-workflow after done -> 200 + ERROR log
- Mismatched payloadId on ack -> 200 + WARN, pending NOT cleared
- Duplicate ACK (already null) -> 200 + WARN

### Design Decisions

1. **`SessionEntry.lastActivityTimestamp` changed to `AtomicReference<Instant>`**: The spec requires
   updating this on every callback, but it was `val Instant` (immutable). Changed to `AtomicReference`
   for thread-safe mutation, matching the existing `pendingPayloadAck` pattern.

2. **`configureApplication()` as public method**: Extracted route/plugin installation into a method
   that accepts an `Application`, so both production CIO embedding and Ktor `testApplication` can
   share the same configuration.

3. **`@Suppress("ReturnCount")`** on `validateAndParseDoneResult`: This validation method has
   early-return-on-error for two distinct validation failures (invalid result, role mismatch).
   The 3 returns are the simplest readable structure for this validation chain.

### Tests

All 1035+ tests pass (including 21 new ShepherdServer tests). Detekt clean.
