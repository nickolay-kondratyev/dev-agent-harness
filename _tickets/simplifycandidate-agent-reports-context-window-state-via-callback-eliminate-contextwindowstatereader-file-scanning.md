---
closed_iso: 2026-03-17T16:22:20Z
id: nid_juduwlev5hizva3k06xg78gxd_E
title: "SIMPLIFY_CANDIDATE: Agent reports context window state via callback — eliminate ContextWindowStateReader file scanning"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:24:12Z
status_updated_iso: 2026-03-17T16:22:20Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, context-window, robustness, agent-communication]
---

## Problem

`ContextWindowStateReader` (ref.ap.ufavF1Ztk6vm74dLAgANY.E) reads context window state from Claude Code's internal file:
`${HOME}/.vintrin_env/claude_code/session/<agentSessionId>/context_window_slim.json`

This is:
- **Fragile**: Depends on Claude Code writing to a specific path with a specific JSON format. Any version change breaks it.
- **Tightly coupled**: V1 implementation (`ClaudeCodeContextWindowStateReader`) is hard-wired to Claude Code's file layout.
- **Hard stop on missing file**: If the file doesn't exist, throws `ContextWindowStateUnavailableException` — hard failure for what could be a timing issue.
- **1-second poll loop**: The hard compaction threshold (ref.ap.8nwz2AHf503xwq8fKuLcl.E) polls this file every second, adding I/O overhead.
- **Dual-signal coupling**: The `fileUpdatedTimestamp` from this file is used as one of the two liveness signals (ref.ap.dnc1m7qKXVw2zJP8yFRE.E), further increasing dependency on file system reads.

## Proposed Simplification

Have the agent periodically report its context window state via an HTTP callback:
```
callback_shepherd.signal.sh context-state <remaining_percentage>
```

This could be triggered by the agent's system prompt instructions (e.g., "report context window state after each tool use") or by a lightweight wrapper.

## Benefits
- **Agent-type agnostic** — works for any agent that can call the callback script.
- **Eliminates file system coupling** — no need to know where any agent stores its internal files.
- **Eliminates polling** — push model instead of pull. Harness receives updates as they happen.
- **Simpler interface** — `ContextWindowStateReader` interface can be replaced by reading from `SessionEntry.lastReportedContextState`.
- **More robust** — no hard stop on missing files; if agent stops reporting, dual-signal liveness (or single-signal, per ticket nid_s6jsgj1f3zuifzyh726d7su95_E) catches it.

## Consideration
Depends on whether the agent can reliably self-report context window state. If not directly available to the agent, a sidecar process watching the file could still push to the callback. But even a sidecar is simpler than the harness doing the file reading.

## Spec files affected
- `doc/use-case/ContextWindowSelfCompactionUseCase.md`
- `doc/use-case/HealthMonitoring.md`
- `doc/core/agent-to-server-communication-protocol.md`
- `doc/core/PartExecutor.md` (health-aware await loop uses context window state)

## WHY-NOT
Agents do not have access to the context percentage

> ❯ I mean the percentage of context that is already used up for this session
> ● No, I don't have visibility into context window usage percentages. CC with GLM-5 convo

Even if they did that doesn't help with interruption example as monitoring the file that is updated through a hook is more reliable.

