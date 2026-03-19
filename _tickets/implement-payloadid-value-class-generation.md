---
id: nid_ejfyrux3m22ww1yl9smu57wwz_E
title: "Implement PayloadId value class + generation"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T00:39:54Z
status_updated_iso: 2026-03-19T17:08:52Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, protocol]
---

## Context

Spec: `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E), section "PayloadId" (ref.ap.GWfkDyTJbMpWhPSPnQHlO.E)

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/server/PayloadId.kt`

### PayloadId value class

Format: `{handshakeGuid_short}-{sequenceN}` — e.g., `a1b2c3d4-3` for the 3rd payload in that session.

- `handshakeGuid_short` = first 8 characters of the HandshakeGuid UUID (excluding the `handshake.` prefix)
- `sequenceN` = per-session `AtomicInteger` counter starting at 1, incremented for each payload sent

```kotlin
@JvmInline
value class PayloadId(val value: String) {
    override fun toString(): String = value

    companion object {
        /**
         * Generates a new PayloadId from the HandshakeGuid and a counter.
         * The counter should be an AtomicInteger owned by the session, starting at 1.
         */
        fun generate(handshakeGuid: HandshakeGuid, counter: AtomicInteger): PayloadId {
            val shortGuid = handshakeGuid.value.removePrefix("handshake.").take(8)
            val seq = counter.getAndIncrement()
            return PayloadId("$shortGuid-$seq")
        }
    }
}
```

### Design Rationale (from spec)
- Counter increment vs. random string generation — no UUID/random library dependency
- Self-correlating: `a1b2c3d4-3` immediately identifies the session (via 8-char prefix) and payload sequence
- Sequence numbers reveal gaps: if payload 2 is ACKed but 3 is not, problem is immediately clear
- Deterministic IDs enable easier test assertions

## Dependencies
- `HandshakeGuid` (already exists at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt`)

## Testing
- Unit test: generate produces correct format `{8chars}-{seq}`
- Unit test: sequential generation increments counter
- Unit test: handshakeGuid_short is first 8 chars after prefix removal
- Unit test: toString returns raw value

