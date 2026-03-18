---
id: nid_say7nutsfmzpi4hdpi5uwm5tc_E
title: "SIMPLIFY_CANDIDATE: PayloadId — use HandshakeGuid prefix + sequence counter instead of random 21-char string"
status: open
deps: []
links: []
created_iso: 2026-03-18T02:10:14Z
status_updated_iso: 2026-03-18T02:10:14Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, observability, debugging]
---

ref.ap.wLpW8YbvqpRdxDplnN7Vh.E (agent-to-server-communication-protocol spec)

Currently each payload sent via AckedPayloadSender gets a random 21-character PayloadId for ACK tracking. These IDs are opaque — correlating a PayloadId to a session requires lookup in SessionsState.

Proposal: Generate PayloadId as {handshakeGuid_short_8chars}-{sequenceN}, e.g., "a1b2c3d4-3" for the 3rd payload in that session. Use an AtomicInteger counter per session.

Why simpler: Counter increment vs. random string generation. No UUID/random library dependency for this.
Why more robust: PayloadId is self-correlating — seeing "a1b2c3d4-3" in logs immediately identifies the session and payload sequence. Sequence numbers reveal gaps (if payload 2 is ACKed but 3 is not, the problem is clear). Deterministic IDs enable easier test assertions.

File: doc/core/agent-to-server-communication-protocol.md

