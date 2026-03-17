---
id: nid_u8aapa26bmut3hob8g85438lo_E
title: "SIMPLIFY_CANDIDATE: Merge /started handshake into first done signal — eliminate two-phase agent startup"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T22:01:32Z
status_updated_iso: 2026-03-17T22:23:02Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, protocol, agent-startup]
---

FEEDBACK:
--------------------------------------------------------------------------------
Currently agent startup uses a TWO-PHASE handshake:
1. Agent starts via TMUX + bootstrap message
2. Agent calls /callback-shepherd/signal/started (3-min startupAckTimeout)
3. Only after /started does harness send full instructions
4. Agent processes instructions and signals done

This creates dual timeout models (startupAckTimeout vs noActivityTimeout), a separate callback type to validate, and a window where the agent is alive but has no instructions yet.

Proposed simplification:
- Remove the /started endpoint and its separate handshake phase
- Harness sends bootstrap + full instructions atomically in the TMUX command (initial prompt argument)
- Agent processes instructions immediately and sends its first done signal
- First done signal serves as proof-of-life + work completion in one signal
- Single noActivityTimeout governs liveness throughout

Robustness gains:
- Eliminates race condition: "agent started but hasnt got full instructions yet"
- Fewer callback types to validate at server
- Single timeout model instead of dual model — simpler health monitoring
- Clearer protocol semantics: agents only signal when they have work to report

Relevant specs:
- doc/core/agent-to-server-communication-protocol.md (/callback-shepherd/signal/started endpoint)
- doc/use-case/HealthMonitoring.md (startup ack timeout concern)

Relevant code:
- Server endpoint handler for /started
- PartExecutor health-aware await loop startup phase
--------------------------------------------------------------------------------

DECISION: KEEP two phase
We have the two phase handshake since getting the ACK back with a GUID is FAST. While the instructions could take a LONG time (over half an hour could be valid). Hence, to know the state of the agent we want to have this 2 phase handshake. Lets have spec document the WHY.