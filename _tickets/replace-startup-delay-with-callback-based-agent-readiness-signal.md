---
closed_iso: 2026-03-12T23:16:18Z
id: nid_827accci9acm2e320fvtddk13_E
title: "Replace startup delay with callback-based agent readiness signal"
status: closed
deps: []
links: []
created_iso: 2026-03-11T01:26:52Z
status_updated_iso: 2026-03-12T23:16:18Z
type: feature
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [agent, tmux, reliability]
---

## Goal

Replace the 5-second `delay()` in `SpawnTmuxAgentSessionUseCase.spawn()` with a callback-based readiness mechanism.

## Current Problem

`SpawnTmuxAgentSessionUseCase` (at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/SpawnTmuxAgentSessionUseCase.kt`) uses `delay(agentStartupDelay)` (default 5s) between creating the tmux session and sending the GUID handshake marker. This is fundamentally fragile:
- Too short: GUID arrives before Claude initializes, consumed by bash shell
- Too long: wastes time on every spawn
- Non-deterministic: startup time varies by system load, model, network

## Proposed Approach

Instead of the harness blindly waiting, the agent should call back to the harness upon startup to signal readiness:

1. The harness provides a CLI script (e.g., `harness-cli-for-agent.sh`) that the agent can call
2. The system prompt instructs the agent to call this CLI with the GUID as its first action
3. The harness listens for this callback (via HTTP endpoint or file signal)
4. Upon receiving the callback with the GUID, the harness knows the agent is ready
5. The harness then proceeds with session ID resolution (no GUID via sendKeys needed)

This eliminates the timing race entirely — the agent self-reports readiness.

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/SpawnTmuxAgentSessionUseCase.kt` — contains the delay
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/impl/ClaudeCodeAgentStarter.kt` — builds the claude CLI command
- `config/prompts/test-agent-system-prompt.txt` — system prompt for test agents
- `app/src/main/kotlin/com/glassthought/shepherd/core/server/` — existing HarnessServer with Ktor

## Acceptance Criteria
- No `delay()` in the spawn flow
- Agent startup readiness is detected via callback, not timer
- Integration test still passes

