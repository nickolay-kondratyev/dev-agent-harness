---
id: nid_juduwlev5hizva3k06xg78gxd_E
title: "SIMPLIFY_CANDIDATE: Agent reports context window state via callback — eliminate ContextWindowStateReader file scanning"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:24:12Z
status_updated_iso: 2026-03-15T01:24:12Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, context-window, robustness, agent-communication]
---

## Problem

`ContextWindowStateReader` (ref.ap.ufavF1Ztk6vm74dLAgANY.E) reads context window state from Claude Code's internal file:\n`${HOME}/.vintrin_env/claude_code/session/<agentSessionId>/context_window_slim.json`\n\nThis is:\n- **Fragile**: Depends on Claude Code writing to a specific path with a specific JSON format. Any version change breaks it.\n- **Tightly coupled**: V1 implementation (`ClaudeCodeContextWindowStateReader`) is hard-wired to Claude Code's file layout.\n- **Hard stop on missing file**: If the file doesn't exist, throws `ContextWindowStateUnavailableException` — hard failure for what could be a timing issue.\n- **1-second poll loop**: The hard compaction threshold (ref.ap.8nwz2AHf503xwq8fKuLcl.E) polls this file every second, adding I/O overhead.\n- **Dual-signal coupling**: The `fileUpdatedTimestamp` from this file is used as one of the two liveness signals (ref.ap.dnc1m7qKXVw2zJP8yFRE.E), further increasing dependency on file system reads.\n\n## Proposed Simplification\n\nHave the agent periodically report its context window state via an HTTP callback:\n```\ncallback_shepherd.signal.sh context-state <remaining_percentage>\n```\n\nThis could be triggered by the agent's system prompt instructions (e.g., "report context window state after each tool use") or by a lightweight wrapper.\n\n## Benefits\n- **Agent-type agnostic** — works for any agent that can call the callback script.\n- **Eliminates file system coupling** — no need to know where any agent stores its internal files.\n- **Eliminates polling** — push model instead of pull. Harness receives updates as they happen.\n- **Simpler interface** — `ContextWindowStateReader` interface can be replaced by reading from `SessionEntry.lastReportedContextState`.\n- **More robust** — no hard stop on missing files; if agent stops reporting, dual-signal liveness (or single-signal, per ticket nid_s6jsgj1f3zuifzyh726d7su95_E) catches it.\n\n## Consideration\nDepends on whether the agent can reliably self-report context window state. If not directly available to the agent, a sidecar process watching the file could still push to the callback. But even a sidecar is simpler than the harness doing the file reading.\n\n## Spec files affected\n- `doc/use-case/ContextWindowSelfCompactionUseCase.md`\n- `doc/use-case/HealthMonitoring.md`\n- `doc/core/agent-to-server-communication-protocol.md`\n- `doc/core/PartExecutor.md` (health-aware await loop uses context window state)

