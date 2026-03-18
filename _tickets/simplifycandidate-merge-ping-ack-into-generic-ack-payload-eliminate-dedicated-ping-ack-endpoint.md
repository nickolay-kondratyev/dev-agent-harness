---
closed_iso: 2026-03-18T14:41:08Z
id: nid_aij16hhr7snq2m72q40n6y3w7_E
title: "SIMPLIFY_CANDIDATE: Merge ping-ack into generic ack-payload — eliminate dedicated ping ACK endpoint"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:24:59Z
status_updated_iso: 2026-03-18T14:41:08Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, protocol, dry]
---

## Problem

Three separate ACK mechanisms serve the same purpose (confirm harness→agent delivery):
1. `/signal/started` — bootstrap ACK
2. `/signal/ping-ack` — health ping ACK
3. `/signal/ack-payload` — generic send-keys ACK (Payload Delivery ACK Protocol)

The `/signal/ping-ack` is functionally a special case of `/signal/ack-payload`. The spec explicitly exempts pings from the generic ACK protocol with circular reasoning: "pings have their own ACK mechanism (ping-ack)."

## Spec Reference

- `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E)
- `doc/use-case/HealthMonitoring.md` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E)

## Proposed Change

Wrap health pings in the same `<payload>` XML with `PayloadId`, reuse `/signal/ack-payload`. Remove `/signal/ping-ack` endpoint and its callback script command. The health monitor detects liveness via the generic ACK — same timeout, same retry logic.

`/signal/started` stays separate because bootstrap uses initial-prompt-argument delivery (not send-keys), so the generic ACK protocol does not apply.

## Justification

- **Simpler protocol**: One fewer endpoint, one fewer callback command, one fewer signal handler.
- **More robust**: Pings use the same battle-tested ACK retry logic as work payloads instead of a separate code path.
- **DRY**: Eliminates duplicate ACK handling logic between ping-ack and ack-payload.


## Notes

**2026-03-18T14:41:18Z**

## Resolution

Merged `/signal/ping-ack` into the generic `/signal/ack-payload` mechanism across all 5 spec files:

- **agent-to-server-communication-protocol.md**: Removed `/signal/ping-ack` endpoint row, removed `ping-ack` callback command, updated scope table (health pings now "Yes"), added health ping to AckedPayloadSender callers table, reduced acknowledgment mechanisms from 3 to 2, updated all prose references.
- **HealthMonitoring.md**: Pings now sent via AckedPayloadSender, proof-of-life uses ack-payload, updated liveness signal list.
- **ContextForAgentProvider.md**: Replaced `callback_shepherd.signal.sh ping-ack` instruction with explanation that pings arrive wrapped in payload XML.
- **PartExecutor.md**: Consolidated ping-ack row into existing ack-payload row in side-channel signals table.
- **high-level.md**: Updated payload delivery ACK description from "except pings" to "including health pings".

`/signal/started` remains separate (bootstrap uses initial-prompt-argument delivery, not send-keys).
