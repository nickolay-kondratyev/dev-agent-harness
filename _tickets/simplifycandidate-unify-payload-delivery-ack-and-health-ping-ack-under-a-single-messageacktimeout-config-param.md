---
closed_iso: 2026-03-17T22:41:26Z
id: nid_lhcx5kzmnj6sbogccpf5ff4a1_E
title: "SIMPLIFY_CANDIDATE: Unify payload-delivery ACK and health-ping ACK under a single messageAckTimeout config param"
status: closed
deps: []
links: []
created_iso: 2026-03-17T22:15:17Z
status_updated_iso: 2026-03-17T22:41:26Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, config, health-monitoring, protocol]
---

Both payload delivery (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) and health ping (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) share the same semantic: "we sent a message, wait for agent callback acknowledgment." Both use a 3-minute default. Both poll SessionEntry.lastActivityTimestamp.

Problem:
- Two separate named timeout params for semantically identical operations.
- When operators tune one, they must remember to tune the other — easy to miss.
- Two separate polling implementations for the same pattern.

Proposed simplification:
- Replace `payloadAckTimeout` and implicit ping-ack timeout with a single `messageAckTimeout: Duration` in HarnessTimeoutConfig.
- Single name, single value, single semantic: "How long to wait for agent to ack any harness message."
- Shared polling utility `awaitMessageAck(session, timeout)` reused by both instructions-delivery and health-ping paths.

Robustness improvement:
- Eliminates accidental misconfiguration from two separate but semantically identical timeout values.
- Single tuning point — clear contract for operators.
- Simpler HarnessTimeoutConfig data class (fewer fields).
- Shared implementation = fewer test cases needed.

Relevant specs: doc/use-case/HealthMonitoring.md, doc/core/agent-to-server-communication-protocol.md

