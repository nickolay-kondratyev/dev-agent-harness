---
closed_iso: 2026-03-17T20:35:53Z
id: nid_erchjt8uu1ghg03z57lrg5fqp_E
title: "SIMPLIFY_CANDIDATE: Replace Payload Delivery ACK protocol with file-based inbox"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:08:25Z
status_updated_iso: 2026-03-17T20:35:53Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, protocol, agent-communication]
---

The Payload Delivery ACK protocol in doc/core/agent-to-server-communication-protocol.md is ~150 lines of spec covering:
- XML wrapping of instruction payloads with PayloadId
- 3-attempt retry with 3 min timeout each
- ACK tracking on SessionEntry (pendingPayloadAck field)
- Duplicate ACK handling
- Mismatched PayloadId handling

This solves: "agent is alive but never received the instruction sent via tmux send-keys."

Proposal: File-based inbox pattern.
- Harness writes instruction content to a known file path (e.g., comm/in/instructions.md)
- Harness sends a SHORT trigger via send-keys: just the file path, not the full payload
- Agent reads instruction from file
- If send-keys is lost, agent can poll the inbox file on a timer

Advantages:
- Eliminates entire ACK protocol (XML wrapping, PayloadId, retry logic, duplicate handling)
- File-based delivery is atomic (write temp + rename) — no partial delivery
- Lost send-keys trigger is self-healing (agent polls inbox)
- Simpler SessionEntry (no pendingPayloadAck field)
- tmux send-keys is unreliable for large payloads; file-based sidesteps this entirely

Files affected:
- doc/core/agent-to-server-communication-protocol.md (remove ACK section)
- doc/core/SessionsState.md (remove pendingPayloadAck from SessionEntry)
- AckedPayloadSender implementation
- Agent bootstrap/callback scripts

