# PayloadId Implementation Review

## Summary

Clean, spec-compliant implementation of `PayloadId` as a `@JvmInline value class`. The implementation
correctly follows the `HandshakeGuid` pattern, matches the spec at `ref.ap.GWfkDyTJbMpWhPSPnQHlO.E`,
and has good test coverage. No critical or important issues found.

**Verdict: APPROVE**

## Verification

- `./sanity_check.sh` — PASSED
- `./gradlew :app:test` — PASSED (all tests green, detekt clean)
- Spec compliance — checked against `doc/core/agent-to-server-communication-protocol.md`

## CRITICAL Issues

None.

## IMPORTANT Issues

None.

## Suggestions

### 1. Consider adding an `@AnchorPoint` annotation

The `HandshakeGuid` pattern reference includes `@AnchorPoint("ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E")`.
The spec references PayloadId via `ref.ap.GWfkDyTJbMpWhPSPnQHlO.E` (which points to the spec section,
not the class). If future code needs to reference the `PayloadId` class itself via anchor point,
adding one now would maintain consistency with `HandshakeGuid`. Low priority — the spec AP may be
sufficient.

### 2. Minor: test for HandshakeGuid without `handshake.` prefix

The `removePrefix` call is safe (returns original string if prefix absent), but there is no test
for a `HandshakeGuid` that does not have the prefix. This is arguably unnecessary since
`HandshakeGuid.generate()` always produces the prefix, and constructing one without it would be
misuse. Noting for completeness only — not actionable.

## What Was Done Well

- Follows the established `HandshakeGuid` value class pattern exactly
- `SHORT_GUID_LENGTH` named constant avoids magic number (detekt compliant)
- `getAndIncrement()` is the correct atomic operation (returns current, then increments)
- Tests cover the three required scenarios: format correctness, sequential increment, prefix removal
- BDD style with `AsgardDescribeSpec`, one assertion per `it` block
- KDoc is clear and captures design rationale
