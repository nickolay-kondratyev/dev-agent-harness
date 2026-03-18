---
id: nid_aij16hhr7snq2m72q40n6y3w7_E
title: "SIMPLIFY_CANDIDATE: Merge ping-ack into generic ack-payload — eliminate dedicated ping ACK endpoint"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:24:59Z
status_updated_iso: 2026-03-18T14:24:59Z
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

