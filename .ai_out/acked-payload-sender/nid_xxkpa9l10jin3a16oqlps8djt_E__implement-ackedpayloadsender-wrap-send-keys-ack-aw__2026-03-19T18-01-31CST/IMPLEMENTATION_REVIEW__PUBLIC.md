# AckedPayloadSender Implementation Review

## Summary: PASS

The implementation is solid and spec-compliant. All tests pass (14 test cases), sanity check passes,
and the code follows project conventions well. The `AtomicReference<PayloadId?>` change to `SessionEntry`
is well-reasoned and consistent with the existing `ConcurrentLinkedQueue` pattern.

No critical or blocking issues found. A few important items and suggestions below.

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. `data class SessionEntry` with `AtomicReference` — equals/hashCode/copy semantics

`SessionEntry` is a `data class`. Adding `AtomicReference<PayloadId?>` means:
- **`equals`/`hashCode`**: Two `SessionEntry` instances with different `AtomicReference` objects
  holding the same `PayloadId` value will NOT be equal (reference equality on the `AtomicReference`
  container, not value equality on the contents).
- **`copy()`**: Copies the reference to the same `AtomicReference` — mutations are shared between
  original and copy.

**However**, this is consistent with the existing fields `CompletableDeferred<AgentSignal>` and
`ConcurrentLinkedQueue<PendingQuestion>`, which have the same reference-equality semantics.
`SessionEntry` is used as a mutable live registry entry (not a value object), so this is
**acceptable** — but it is worth being aware that `data class` semantics are misleading here.

**Verdict**: Not a bug today, but worth a follow-up ticket to consider whether `SessionEntry`
should be a regular `class` instead of `data class` (since its `equals`/`hashCode`/`copy` are
not value-based anyway).

### 2. Logging uses generic `ValType.STRING_USER_AGNOSTIC` for all structured values

The implementation logs `payloadId`, `attempt`, and `maxAttempts` all with
`ValType.STRING_USER_AGNOSTIC`. Per CLAUDE.md: "ValType must be semantically specific to the value
being logged." Consider defining project-specific `ValTypeV2` entries for payload-related logging
(e.g., `PAYLOAD_ID`, `ATTEMPT_NUMBER`), or use existing domain-appropriate types if available.

This is not blocking — the logging works and is structured — but using generic types reduces
the value of structured logging for filtering and analysis.

## Suggestions

### 1. `wrapPayload` as companion function — consider making it a top-level private function or a separate object

`wrapPayload` is a static utility on the companion object, marked "visible for testing." This is
pragmatic and works. If more wrapping/parsing functions emerge, consider extracting to a focused
`PayloadWrapper` class. For now, this is fine.

### 2. Test helper duplication — `createTestTmuxAgentSession` and `createTestSessionEntry`

Both `AckedPayloadSenderTest.kt` and `SessionEntryTest.kt` define their own
`createTestTmuxAgentSession` and `createTestSessionEntry` helpers with very similar logic.
Consider extracting shared test fixtures into a common test helper file to DRY this up.
Not urgent for two files, but will compound as more tests reference `SessionEntry`.

### 3. Sequential calls test has two assertions in one `it` block

In the "WHEN sendAndAwaitAck is called twice" test (line 285), there are two `shouldContain`
assertions on `sentPayloadIds[0]` and `sentPayloadIds[1]` in a single `it` block. The project
standard is one assert per test. This could be split into two `it` blocks, though the current
form is understandable since these two assertions are testing a single logical property (sequential
counter increment).

## Positive Notes

- **Spec compliance is exact**: XML format matches the spec character-for-character, verified by the
  `THEN matches the exact spec format` test.
- **Same PayloadId across retries**: Correctly generated once before the retry loop, reused in all
  attempts — matches the spec requirement.
- **`pendingPayloadAck` set BEFORE `sendKeys`**: The test explicitly captures the value at send-time
  via the spy's `onSend` callback. This validates the ordering guarantee the spec requires.
- **Configurable timeouts**: `ackTimeout`, `pollInterval`, and `maxAttempts` are constructor params
  with sensible defaults, enabling fast tests without compromising production behavior.
- **`SpyTmuxCommunicator`**: Clean, focused test double. The `onSend` callback pattern for
  simulating ACKs is elegant.
- **Exception hierarchy**: `PayloadAckTimeoutException` correctly extends `AsgardBaseException`.
- **No `delay` in tests**: Tests use synchronous ACK simulation via spy callbacks, not timing-based
  synchronization.
- **Constants**: All magic values are named constants (`ACK_TIMEOUT_DEFAULT`, `POLL_INTERVAL_DEFAULT`,
  `MAX_ATTEMPTS_DEFAULT`). Protocol vocabulary uses `ProtocolVocabulary` constants.
- **Thread safety**: `AtomicReference` for cross-thread read/write of `pendingPayloadAck` is correct.
  The polling loop uses `System.nanoTime()` (monotonic clock) for deadline — correct choice over
  wall-clock time.
