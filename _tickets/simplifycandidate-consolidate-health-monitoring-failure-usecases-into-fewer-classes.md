---
closed_iso: 2026-03-17T17:46:01Z
id: nid_9ein5ehx3nqngxs42vk9umr7v_E
title: "SIMPLIFY_CANDIDATE: Consolidate health monitoring failure UseCases into fewer classes"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:14:40Z
status_updated_iso: 2026-03-17T17:46:01Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, health-monitoring, dry]
---

Health monitoring (ref.ap.RJWVLgUGjO5zAwupNLhA0.E, doc/use-case/HealthMonitoring.md) defines five distinct UseCase classes:
1. NoStartupAckUseCase — no callback within 3 min of spawn
2. NoStatusCallbackTimeOutUseCase — no activity within noActivityTimeout (30 min)
3. NoReplyToPingUseCase — agent did not respond to ping within 3 min
4. FailedToExecutePlanUseCase — general execution failure handler
5. FailedToConvergeUseCase — iteration budget exceeded

The first three (1-3) all represent the same conceptual event: **the agent is unresponsive**. The only difference is detection context (when was unresponsiveness detected). Each results in the same outcome: kill TMUX session, return AgentCrashed.

**Simplification:** Merge NoStartupAckUseCase, NoStatusCallbackTimeOutUseCase, and NoReplyToPingUseCase into a single `AgentUnresponsiveUseCase` with a detection-context parameter (enum: STARTUP_TIMEOUT, NO_ACTIVITY_TIMEOUT, PING_TIMEOUT). Same logging and outcome logic, one class instead of three. And make sure the same context logging wise is preserved so we understand WHY this use case was triggerred.

Keep FailedToExecutePlanUseCase and FailedToConvergeUseCase separate — they have fundamentally different semantics (general failure handling and budget exhaustion, respectively).

**Robustness improvement:** Single failure-handling path for unresponsive agents eliminates divergence risk between three classes that should behave identically. Easier to add new detection triggers (just add an enum value). Fewer classes to wire in TicketShepherdCreator.

**Result:** 5 classes → 3 classes. Three paths that diverge only in log message → one path with parameterized logging.

**Spec files affected:** doc/use-case/HealthMonitoring.md (merge three UseCases into one).

