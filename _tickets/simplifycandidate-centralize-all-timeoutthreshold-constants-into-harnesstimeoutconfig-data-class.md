---
closed_iso: 2026-03-17T21:32:40Z
id: nid_c6uvtzkas9kkjnp82r89qeskd_E
title: "SIMPLIFY_CANDIDATE: Centralize all timeout/threshold constants into HarnessTimeoutConfig data class"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:23:25Z
status_updated_iso: 2026-03-17T21:32:40Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, config, robustness, testing, magic-numbers]
---


## Notes

**2026-03-17T21:23:50Z**

Timeout and threshold constants are scattered across PartExecutorImpl, ContextWindowSelfCompactionUseCase, AckedPayloadSender/AgentFacadeImpl. Violates DRY, makes tuning hard, forces tests to know magic numbers. Solution: create data class HarnessTimeoutConfig(startupAckTimeout=3min, healthCheckInterval=5min, noActivityTimeout=30min, pingTimeout=3min, payloadAckTimeout=3min, payloadAckRetries=3, selfCompactionTimeout=5min, contextWindowSoftThresholdPct=35, contextWindowHardThresholdPct=20). Wire through TicketShepherdCreator. Tests inject fast-timeout variants. Optional: read from env vars. See: doc/use-case/HealthMonitoring.md (Configuration section), doc/use-case/ContextWindowSelfCompactionUseCase.md (Thresholds), doc/core/PartExecutor.md (Dependencies).

**2026-03-17T21:32:56Z**

Completed. Created HarnessTimeoutConfig data class in com.glassthought.shepherd.core.data with all 9 timeout/threshold fields and production defaults. Added forTests() factory for fast test timeouts. Wired into ShepherdContext.timeoutConfig. Updated spec docs (HealthMonitoring.md, ContextWindowSelfCompactionUseCase.md, PartExecutor.md) to reference HarnessTimeoutConfig fields. All tests pass.
