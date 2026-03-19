# AckedPayloadSender — Implementation Private Context

## Status: COMPLETE

## What Was Implemented

- `AckedPayloadSender` interface + `AckedPayloadSenderImpl` at `app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt`
- `PayloadAckTimeoutException` at `app/src/main/kotlin/com/glassthought/shepherd/core/server/PayloadAckTimeoutException.kt`
- Tests at `app/src/test/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSenderTest.kt`
- Modified `SessionEntry.pendingPayloadAck` from `val pendingPayloadAck: PayloadId?` to `val pendingPayloadAck: AtomicReference<PayloadId?>`

## Anchor Point

- `ap.m6X7We58LwUAu4khybhtZ.E` — assigned to `AckedPayloadSender` interface

## Design Decisions

1. **AtomicReference for pendingPayloadAck**: Follows the same pattern as `questionQueue` (ConcurrentLinkedQueue on a val field). Thread-safe mutation without needing external registry.
2. **payloadCounter as constructor parameter**: The AtomicInteger counter is owned per-session by the caller (facade). This keeps AckedPayloadSenderImpl stateless regarding session identity.
3. **Configurable timeouts**: ackTimeout, pollInterval, maxAttempts are constructor parameters with production defaults. Tests use fast values (50ms timeout, 10ms poll).
4. **wrapPayload as companion function**: Allows direct unit testing of XML format without needing a full sender instance.
5. **Polling with delay**: Uses `kotlinx.coroutines.delay` in the polling loop. This is coroutine-friendly and does not block threads.

## Test Patterns

- `SpyTmuxCommunicator`: Records sendKeys calls, executes `onSend` callback synchronously to simulate ACK timing
- `logCheckOverrideAllow(LogLevel.WARN)` on it blocks that produce expected WARN log lines (retry timeout scenarios)
- `AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true)` to clear log lines between tests

## All Tests Pass

`./gradlew :app:test` — BUILD SUCCESSFUL, all tests green.
