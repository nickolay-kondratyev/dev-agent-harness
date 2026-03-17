---
id: nid_26k7z10myi22rryyosg59vu55_E
title: "SIMPLIFY_CANDIDATE: Agent reports session ID in /started callback — eliminate JSONL file scanning"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:24:09Z
status_updated_iso: 2026-03-15T01:24:09Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, agent-spawn, robustness]
---

## Problem

AgentSessionIdResolver (ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E) resolves the agent session ID by scanning Claude Code internal JSONL files (`$HOME/.claude/projects/.../*.jsonl`) for the GUID string after `/signal/started` is received.

This is:
- **Fragile**: Depends on Claude Code internal file structure that could change between versions.
- **Slow**: 45-second timeout with 500ms poll interval. Adds latency to every agent spawn.
- **Tightly coupled**: Each agent type needs its own resolver implementation that understands the agent's internal file layout.

## Proposed Simplification

Have the agent include its session ID as a field in the `/signal/started` callback payload:
```
callback_shepherd.signal.sh started --session-id <id>
```

The agent knows its own session ID — let it report it directly.

## Benefits
- **Eliminates** `AgentSessionIdResolver` interface and all implementations entirely.
- **Removes** file system coupling to agent internals.
- **Removes** 45-second polling timeout — session ID available immediately with the started signal.
- **Agent-type agnostic** — any agent can report its session ID; no per-agent resolver needed.
- **More robust** — no race condition between agent creating its session files and harness scanning for them.

## Impact
- `SpawnTmuxAgentSessionUseCase` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) simplified — step 6 becomes trivial.
- `SessionEntry` `agentSessionId` populated at callback time, not after async file scan.
- Bootstrap script updated to pass session ID.

## Spec files affected
- `doc/use-case/SpawnTmuxAgentSessionUseCase.md`
- `doc/core/agent-to-server-communication-protocol.md`

