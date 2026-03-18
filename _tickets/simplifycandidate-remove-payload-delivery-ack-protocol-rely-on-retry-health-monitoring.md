---
id: nid_y0sqymwju41un34graunce3i2_E
title: "SIMPLIFY_CANDIDATE: Remove Payload Delivery ACK Protocol — rely on retry + health monitoring"
status: open
deps: []
links: []
created_iso: 2026-03-18T15:08:39Z
status_updated_iso: 2026-03-18T15:08:39Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, protocol, robustness]
---

## Problem

The Payload Delivery ACK Protocol (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E in `doc/core/agent-to-server-communication-protocol.md`) introduces substantial protocol machinery:
- XML payload wrapping
- PayloadId scheme (8-char GUID prefix + sequence number)
- `/ack-payload` endpoint on the server
- `pendingPayloadAck` field on `SessionEntry`
- `AckedPayloadSender` abstraction
- 3 retries × 3-minute timeout = 9-minute worst-case window

This is the middle layer of a three-layer delivery assurance model (callback retry → ACK protocol → health monitoring). The bottom layer (health monitoring) already catches the exact failure mode the ACK protocol targets: "agent is alive in tmux but never received/processed the instruction."

## Proposed Simplification

Remove the ACK protocol entirely. Replace with:
1. **Simple send-keys retry**: On `send-keys` call, retry 2-3 times with 1-second delays if tmux reports an error.
2. **Rely on health monitoring**: The `noActivityTimeout` already detects agents that never process instructions. Detection is slightly slower (minutes vs seconds) but the failure mode is rare.

## What Gets Removed
- XML wrapping logic
- PayloadId generation and tracking
- `/ack-payload` HTTP endpoint
- `pendingPayloadAck` field from `SessionEntry` (ref.ap.7V6upjt21tOoCFXA7nqNh.E)
- `AckedPayloadSender` class
- Phase A (ACK-await) from the health-aware await loop in `doc/core/PartExecutor.md`

## Why This Is Both Simpler AND More Robust
- **Simpler**: Removes an entire protocol layer (~6 concepts, 1 endpoint, 1 SessionEntry field)
- **More robust**: Fewer moving parts = fewer ways to fail. The ACK protocol itself can have bugs (e.g., agent receives instruction but ACK callback fails due to network glitch → harness retries sending instruction → agent gets confused by duplicate instruction). Health monitoring is battle-tested and covers this case without the intermediate protocol.
- **Less agent coupling**: Agents no longer need to understand/implement the ACK callback protocol.

