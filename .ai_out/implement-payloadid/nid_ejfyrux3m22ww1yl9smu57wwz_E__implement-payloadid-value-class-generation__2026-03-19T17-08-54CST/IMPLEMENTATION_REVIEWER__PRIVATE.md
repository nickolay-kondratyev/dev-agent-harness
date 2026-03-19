# PayloadId Review — Private Notes

## Review Checklist

- [x] Sanity check passes
- [x] Full `:app:test` passes
- [x] Implementation matches spec (`ref.ap.GWfkDyTJbMpWhPSPnQHlO.E`)
- [x] Format: `{handshakeGuid_short}-{sequenceN}` — correct
- [x] `handshakeGuid_short` = first 8 chars after `handshake.` prefix removal — correct
- [x] `sequenceN` via `AtomicInteger` starting at 1 — correct
- [x] `@JvmInline value class` — correct
- [x] `toString()` override — correct
- [x] `generate(handshakeGuid, counter)` companion factory — correct
- [x] Tests: format, sequential increment, prefix removal, toString — all present
- [x] Follows `HandshakeGuid` pattern reference
- [x] No existing tests removed
- [x] No functionality lost
- [x] Named constant for magic number 8

## Security Check
- No injection risk (value class wrapping a String, no external input parsing)
- No secrets, no credentials

## Architecture Check
- Correct package: `com.glassthought.shepherd.core.server` — PayloadId is a server-side concept
  (used in payload wrapping sent to agents)
- AtomicInteger is externally owned (passed in), so PayloadId itself is stateless — good design

## Edge Cases Considered
- What if HandshakeGuid doesn't have `handshake.` prefix? `removePrefix` is safe, returns original.
  Not a real concern since `HandshakeGuid.generate()` always adds the prefix.
- What if UUID portion is shorter than 8 chars? `take(8)` is safe, returns whatever is available.
  Not a real concern since UUIDs are 36 chars.
- Thread safety: AtomicInteger guarantees unique sequence numbers across threads. Correct.

## Verdict
Simple, clean, spec-compliant. No issues to block on. Approve.
