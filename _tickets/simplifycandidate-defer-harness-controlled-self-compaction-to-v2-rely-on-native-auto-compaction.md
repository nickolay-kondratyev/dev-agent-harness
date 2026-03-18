---
closed_iso: 2026-03-18T15:36:34Z
id: nid_k5pinoo8bk3lapv9fn9cdm36e_E
title: "SIMPLIFY_CANDIDATE: Defer harness-controlled self-compaction to V2 — rely on native auto-compaction"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:58:09Z
status_updated_iso: 2026-03-18T15:36:34Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, self-compaction, v1-scope]
---

## Problem
The harness-controlled self-compaction flow (doc/use-case/ContextWindowSelfCompactionUseCase.md, ref.ap.8nwz2AHf503xwq8fKuLcl.E) adds significant complexity to V1:
- SELF_COMPACTED signal endpoint + AgentSignal.SelfCompacted variant
- performCompaction flow (send instruction → await signal → validate PRIVATE.md → git commit → kill session)
- Session rotation (handle = null → lazy respawn) adding nullable state to PartExecutor
- PRIVATE.md lifecycle management (creation, persistence, inclusion by ContextForAgentProvider)
- ContextWindowStateReader interface + ClaudeCodeContextWindowStateReader implementation
- Done-boundary compaction check after every done signal
- 5-min compaction timeout + contextFileStaleTimeout in HarnessTimeoutConfig

## Proposal
In V1, rely solely on Claude Code native auto-compaction (which the spec already acknowledges stays enabled). Defer harness-controlled compaction to V2.

## What Gets Eliminated
- SELF_COMPACTED signal endpoint
- AgentSignal.SelfCompacted variant
- performCompaction() in AgentFacade/AgentFacadeImpl
- Session rotation logic in PartExecutor
- PRIVATE.md lifecycle (creation, ContextForAgentProvider inclusion)
- ContextWindowStateReader interface and implementation
- Compaction-related timeout constants from HarnessTimeoutConfig
- self-compaction instruction template

## Why More Robust
- Session rotation has state management risks (nullable handle, respawn logic, git commit during compaction)
- Native auto-compaction is battle-tested by Claude Code across millions of sessions
- Removes a failure point during agent execution (compaction instruction could itself fail)
- Simpler PartExecutor state machine (no handle nullification mid-execution)

## Risk Mitigation
- Native auto-compaction preserves conversation context (Claude Code already optimizes for this)
- Agents write PUBLIC.md each iteration, so essential output is captured in files regardless
- If native compaction proves insufficient, V2 adds harness-controlled compaction with full spec already written

## Affected Specs
- doc/use-case/ContextWindowSelfCompactionUseCase.md (scope to V2-only or archive)
- doc/core/PartExecutor.md (remove compaction check from flow)
- doc/core/AgentFacade.md (remove readContextWindowState method)
- doc/core/agent-to-server-communication-protocol.md (remove self-compacted endpoint)
- doc/core/ContextForAgentProvider.md (remove PRIVATE.md inclusion)
- doc/schema/ai-out-directory.md (PRIVATE.md becomes V2-only)

