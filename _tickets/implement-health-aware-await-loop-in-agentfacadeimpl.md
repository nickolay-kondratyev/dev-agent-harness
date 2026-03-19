---
id: nid_qdd1w86a415xllfpvcsf8djab_E
title: "Implement Health-Aware Await Loop in AgentFacadeImpl"
status: open
deps: [nid_0of6zl2493ctvmy9m23kxnhnl_E, nid_sfgmp7jk8tlnnh2sgblh3npkj_E, nid_p1w49sk0s2isnvcjbmhgapho7_E]
links: []
created_iso: 2026-03-18T17:37:59Z
status_updated_iso: 2026-03-18T17:37:59Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [health-monitoring, core, agent-facade]
---

## Context

Spec: `doc/use-case/HealthMonitoring.md` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E), sections "Monitoring Loop — AgentFacadeImpl-Owned" (ap.6HIM68gd4kb8D2WmvQDUK.E) and "Flow" (lines 29-36).

The health monitoring loop is **owned by AgentFacadeImpl** (inside `sendPayloadAndAwaitSignal`), not a separate background component. This eliminates race conditions between a monitor and the server competing to complete the same `CompletableDeferred<AgentSignal>`.

## What to Implement

The health-aware await loop inside `AgentFacadeImpl.sendPayloadAndAwaitSignal()`. This is the core integration that ties all health monitoring UseCases together.

### Flow (from spec)

1. **Startup acknowledgment** (`healthTimeouts.startup`, default: 3 min): After spawn, use shorter startup window. If no callback of any kind arrives within this window → trigger `AgentUnresponsiveUseCase` with `DetectionContext.STARTUP_TIMEOUT`. Once any callback arrives, switch to `normalActivity` window.

2. **Payload delivery ACK** (default: 3 min per attempt, 3 attempts max): After sending instructions via `send-keys`, await `/signal/ack-payload` before entering signal-await loop. If no ACK → retry (up to 2 retries). All retries exhausted → `AgentCrashed`. See ref.ap.r0us6iYsIRzrqHA5MVO0Q.E.

3. **No activity timeout** (`healthTimeouts.normalActivity`, default: 30 min): If no HTTP callback arrives within window → trigger `AgentUnresponsiveUseCase` with `DetectionContext.NO_ACTIVITY_TIMEOUT`.

4. **Ping**: Harness sends health ping to agent via TMUX send-keys (wrapped with `AckedPayloadSender`). Agent ACKs via generic `/signal/ack-payload` mechanism.

5. **Ping response timeout** (`healthTimeouts.pingResponse`, default: 3 min): After ping, re-check `lastActivityTimestamp`. If ANY callback arrived during ping window → alive → loop back to step 3. If no callback → `AgentUnresponsiveUseCase` with `DetectionContext.PING_TIMEOUT`.

6. **Crash handling (V1)**: Kill TMUX session, complete `signalDeferred` with `AgentSignal.Crashed` → executor returns `PartResult.AgentCrashed` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase`.

### Why AgentFacadeImpl-Owned (from spec)
- **Single control flow**: facade creates deferred, registers it, awaits it. Health checks in same owner = no competing `complete()` calls.
- **Structured concurrency**: health check scoped to facade await lifetime.
- **Timer reset via shared state**: server updates `SessionEntry.lastActivityTimestamp` on every callback. Facade reads this on each health tick.

### Proof-of-Life Principle
After ping, re-check `lastActivityTimestamp`. If ANY callback arrived (user-question, done, ack-payload, etc.), agent is alive. The ping ACK itself serves as proof-of-life for idle-but-alive agents.

### Q&A Suppression (edge case)
When `isQAPending == true` (derived from `questionQueue.isNotEmpty()`), the health-aware loop SKIPS all health checks. No pings, no noActivityTimeout. Rationale: agent is in known-idle state waiting for Q&A answer; pings would waste context window. After Q&A completes → health monitoring resumes.

### Liveness Model: HTTP Callbacks Only (ap.dnc1m7qKXVw2zJP8yFRE.E)
- Liveness determined SOLELY by HTTP callback timestamps (`lastActivityTimestamp`)
- `context_window_slim.json` is for compaction decisions ONLY, NOT liveness

### Tick Interval
- `healthCheckInterval` (default: 5 min) from HarnessTimeoutConfig
- This is the polling frequency for checking `lastActivityTimestamp` staleness

## Dependencies (must exist before implementation)
- `AgentFacadeImpl` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — the class being modified
- `SessionsState` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) — provides `lastActivityTimestamp`, `isQAPending`
- `HealthTimeoutLadder` in `HarnessTimeoutConfig` (ticket: nid_0of6zl2493ctvmy9m23kxnhnl_E)
- `AgentUnresponsiveUseCase` (ticket: nid_sfgmp7jk8tlnnh2sgblh3npkj_E)
- `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E) — for ping delivery

## Testability
- Unit tests via `FakeAgentFacade` + virtual time (`TestClock` + `kotlinx-coroutines-test`)
- `FakeAgentFacade` controls `lastActivityTimestamp` advancement
- `TestClock` (ref.ap.whDS8M5aD2iggmIjDIgV9.E) controls `now()` for age comparisons
- `advanceTimeBy()` controls coroutine delays (1-second ticks, timeout windows)
- Tests verify every decision branch: standard ping, crash declaration, proof-of-life acceptance, Q&A suppression

## Unit Tests (BDD/DescribeSpec)
- GIVEN agent spawned WHEN no callback within startup timeout THEN STARTUP_TIMEOUT triggered
- GIVEN agent active WHEN callback arrives before normalActivity timeout THEN no action (healthy)
- GIVEN agent active WHEN lastActivityTimestamp stale beyond normalActivity THEN ping sent
- GIVEN ping sent WHEN callback arrives during pingResponse window THEN agent marked alive, loop resets
- GIVEN ping sent WHEN no callback after pingResponse THEN PING_TIMEOUT → session killed → AgentCrashed
- GIVEN Q&A pending WHEN normalActivity timeout would trigger THEN health checks skipped (no ping)
- GIVEN Q&A completed WHEN normalActivity timeout reached THEN health checks resume normally

## What Runs Where (from spec)
| Concern | Owner |
|---------|-------|
| Update lastActivityTimestamp | ShepherdServer (on every callback) |
| Check startup ack timeout | AgentFacadeImpl (health-aware loop) |
| Check lastActivityTimestamp staleness | AgentFacadeImpl (health-aware loop) |
| Send ping via TMUX | AgentFacadeImpl (delegates to AgentUnresponsiveUseCase) |
| Post-ping check | AgentFacadeImpl (after pingResponse window) |
| Declare crash, kill TMUX | AgentFacadeImpl (via AgentUnresponsiveUseCase PING_TIMEOUT) |

## Acceptance Criteria
- Health-aware await loop integrated into AgentFacadeImpl.sendPayloadAndAwaitSignal
- Startup → normalActivity → ping → crash escalation ladder works
- Q&A suppression prevents wasteful pings
- All health decisions logged with structured values
- Comprehensive unit tests with virtual time

