---
closed_iso: 2026-03-17T17:14:19Z
id: nid_hjyxrijwqy4h164jzuw8r6y91_E
title: "SIMPLIFY_CANDIDATE: Collapse two-phase agent spawn into single-phase for V1"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:31:03Z
status_updated_iso: 2026-03-17T17:14:19Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, agent-spawn, robustness]
---

## Current State
SpawnTmuxAgentSessionUseCase (doc/use-case/SpawnTmuxAgentSessionUseCase.md) uses a two-phase spawn:
1. Bootstrap phase: initial prompt with handshake guid, agent confirms identity via /started callback
2. Work phase: send-keys with full instructions.md content

This creates an intermediate state where the agent has started but has not yet received its work instructions.

## Proposed Simplification
Combine bootstrap message and work instructions into a single initial prompt argument. The agent receives everything in one shot and reports /started when ready. This eliminates:
- The intermediate "started but no instructions" state
- The second send-keys call for instructions.md delivery
- The AckedPayloadSender usage for the initial instruction delivery
- A potential failure mode (send-keys fails after successful spawn)

The /started callback still confirms identity and liveness — the handshake protocol is preserved.

## Why This Improves Robustness
- Removes a failure mode: if send-keys fails between phases, the agent is alive but idle with no instructions
- Atomic delivery: the agent either gets everything or nothing
- Simpler SpawnTmuxAgentSessionUseCase (14-step flow reduced to ~8 steps)

## Spec references
- doc/use-case/SpawnTmuxAgentSessionUseCase.md (14-step spawn flow)
- doc/core/agent-to-server-communication-protocol.md (payload delivery, startup acknowledgment)

