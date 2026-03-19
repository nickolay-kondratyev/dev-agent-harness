---
closed_iso: 2026-03-19T17:54:00Z
id: nid_sfgmp7jk8tlnnh2sgblh3npkj_E
title: "Implement DetectionContext enum + AgentUnresponsiveUseCase"
status: closed
deps: [nid_0of6zl2493ctvmy9m23kxnhnl_E]
links: []
created_iso: 2026-03-18T17:36:26Z
status_updated_iso: 2026-03-19T17:54:00Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [health-monitoring, use-case]
---

## Context

Spec: `doc/use-case/HealthMonitoring.md` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E), section "AgentUnresponsiveUseCase — DetectionContext".

The three previously separate unresponsive-agent UseCases are consolidated into a single `AgentUnresponsiveUseCase` parameterized by a `DetectionContext` enum. All contexts result in the same outcome (kill TMUX session, return AgentCrashed) with context-specific structured logging.

## What to Implement

### 1. DetectionContext enum
```kotlin
enum class DetectionContext {
    /** No callback of any kind within healthTimeouts.startup after spawn */
    STARTUP_TIMEOUT,
    /** lastActivityTimestamp stale beyond healthTimeouts.normalActivity */
    NO_ACTIVITY_TIMEOUT,
    /** No callback after healthTimeouts.pingResponse window */
    PING_TIMEOUT
}
```

### 2. AgentUnresponsiveUseCase
Interface + implementation:
- Constructor-injected dependencies: Out logger, TmuxSessionManager (or AgentFacade killSession)
- Single `suspend fun handle(context: DetectionContext, sessionDetails: ...)` method
- Context-specific structured logging using `Val` + `ValType`:
  - `STARTUP_TIMEOUT`: session name, HandshakeGuid, env vars, timeout duration
  - `NO_ACTIVITY_TIMEOUT`: session name, stale duration, normalActivity value
  - `PING_TIMEOUT`: session name, ping response duration
- Action:
  - `NO_ACTIVITY_TIMEOUT` → sends ping via TMUX send-keys (via AckedPayloadSender)
  - `STARTUP_TIMEOUT` and `PING_TIMEOUT` → kill TMUX session, signal AgentCrashed

### 3. Unit Tests (BDD/DescribeSpec)
- GIVEN STARTUP_TIMEOUT WHEN handle called THEN session killed + AgentCrashed + structured log emitted
- GIVEN NO_ACTIVITY_TIMEOUT WHEN handle called THEN ping sent (NOT crash)
- GIVEN PING_TIMEOUT WHEN handle called THEN session killed + AgentCrashed + structured log emitted
- Verify structured logging for each context (Val types, message strings)

## Package
`com.glassthought.shepherd.usecase.healthmonitoring`

## Spec References
- DetectionContext table: doc/use-case/HealthMonitoring.md lines 163-174
- Design rationale: doc/use-case/HealthMonitoring.md lines 176-183
- Logging principle: doc/use-case/HealthMonitoring.md lines 14-25

## Acceptance Criteria
- DetectionContext enum with 3 variants
- AgentUnresponsiveUseCase handles all 3 detection contexts with correct actions
- All health monitoring decisions logged with structured values
- Unit tests cover all 3 branches

