---
closed_iso: 2026-03-18T13:26:59Z
id: nid_mn0nrr1l8817c0kzvsknbrmxj_E
title: "SIMPLIFY_CANDIDATE: Eliminate healthCheckInterval — check health on every 1-second tick"
status: closed
deps: []
links: []
created_iso: 2026-03-17T23:53:30Z
status_updated_iso: 2026-03-18T13:26:59Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec-change]
---

The health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) already ticks every 1 second for context window monitoring. The `healthCheckInterval` (default 5 min, defined in `doc/use-case/HealthMonitoring.md`) adds a separate cadence for checking `lastActivityTimestamp` staleness.

**Problem**: Two independent timing cadences in the same loop — the 1-second tick for context window and the 5-minute interval for health checks. This adds:
- A `lastHealthCheck` timestamp variable tracked across loop iterations
- The `healthCheckInterval` configuration parameter in `HarnessTimeoutConfig`
- Worst-case detection delay of `normalActivity + healthCheckInterval` = 35 minutes (instead of the documented 30 minutes)

**Simplification**: Check `lastActivityTimestamp` on every 1-second tick alongside the context window check. Timestamp comparison is a single subtraction — essentially free.

**What changes**:
- Remove `healthCheckInterval` from `HarnessTimeoutConfig` (in `doc/use-case/HealthMonitoring.md`)
- Remove `lastHealthCheck` tracking from the health-aware await loop pseudocode (in `doc/core/PartExecutor.md`)
- The loop body becomes: check signal → check Q&A gate → check context window → check lastActivityTimestamp staleness. One cadence, not two.

**Robustness improvement**: Detection latency improves from worst-case 35 min to exactly `normalActivity` (30 min). No edge case where healthCheckInterval adds unexpected delay.

**Spec files to update**:
- `doc/use-case/HealthMonitoring.md` — remove healthCheckInterval from All Health Parameters table
- `doc/core/PartExecutor.md` — simplify health-aware await loop pseudocode (remove lastHealthCheck variable)


## Notes

**2026-03-18T13:27:05Z**

Completed. Removed healthCheckInterval from HarnessTimeoutConfig's 'All Health Parameters' table in doc/use-case/HealthMonitoring.md. Simplified the health-aware await loop pseudocode in doc/core/PartExecutor.md: removed lastHealthCheck variable (both before loop and inside loop), changed awaitSignalWithTimeout(healthCheckInterval) to awaitSignalWithTimeout(1.second). Detection latency now exactly normalActivity (30 min) instead of worst-case 35 min.
