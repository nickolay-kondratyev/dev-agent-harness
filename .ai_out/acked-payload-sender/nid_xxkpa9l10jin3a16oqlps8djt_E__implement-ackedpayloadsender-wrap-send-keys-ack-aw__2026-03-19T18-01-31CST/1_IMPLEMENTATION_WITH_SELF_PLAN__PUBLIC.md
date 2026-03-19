# AckedPayloadSender Implementation

## What Was Done

Implemented `AckedPayloadSender` — the sole gateway for all harness→agent `send-keys` communication
with delivery confirmation via the Payload Delivery ACK Protocol (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).

### Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt` | Interface + `AckedPayloadSenderImpl` (ap.m6X7We58LwUAu4khybhtZ.E) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/server/PayloadAckTimeoutException.kt` | Exception for retry exhaustion |
| `app/src/test/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSenderTest.kt` | Comprehensive BDD tests (14 test cases) |

### Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` | Changed `pendingPayloadAck` from `PayloadId?` to `AtomicReference<PayloadId?>` |
| `app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionEntryTest.kt` | Updated to use `AtomicReference` constructor |

## Key Design Decision

**`pendingPayloadAck` changed to `AtomicReference<PayloadId?>`** — follows the same pattern as
`questionQueue` which is a `val ConcurrentLinkedQueue` (mutable container on an immutable field).
The server callback thread clears it on ACK arrival while the orchestration coroutine polls it.
This avoids needing an external mutable holder/registry.

## Implementation Details

- `AckedPayloadSenderImpl` takes an `AtomicInteger` counter for PayloadId generation (per-session, owned by caller)
- XML wrapping uses `ProtocolVocabulary` constants for tag names, script name, and signal name
- Configurable timeout, poll interval, and max attempts for testability (defaults: 3 min, 100ms, 3 attempts)
- `wrapPayload` is a companion object function — visible for direct testing of XML format
- Tests use `SpyTmuxCommunicator` that simulates ACK by clearing `pendingPayloadAck` synchronously during `sendKeys`

## Test Coverage

1. XML wrapping format matches spec exactly (5 assertions)
2. PayloadId uses HandshakeGuid + counter
3. PayloadId is set on SessionEntry BEFORE send-keys
4. Immediate ACK → returns normally, single send
5. First attempt timeout, ACK on retry → returns normally
6. All retries exhausted → throws `PayloadAckTimeoutException`
7. Sequential calls use incrementing counter
