---
closed_iso: 2026-03-18T15:35:57Z
id: nid_qemtp595umzazuhbhlnn1c517_E
title: "SIMPLIFY_CANDIDATE: Remove ContextWindowState staleness detection in V1"
status: closed
deps: []
links: []
created_iso: 2026-03-18T15:29:41Z
status_updated_iso: 2026-03-18T15:35:57Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

## Current State
In `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E), `ContextWindowState` uses a 3-case model:
1. **Value present** (remainingPercentage from file)
2. **Stale** (file exists but `fileUpdatedTimestamp` older than `contextFileStaleTimeout`)
3. **Missing** (file does not exist)

The staleness detection requires:
- Tracking `fileUpdatedTimestamp` alongside the value
- A `contextFileStaleTimeout` configuration parameter
- Nullable `remainingPercentage` (null when stale or missing)
- Null-handling code in every consumer

This complexity exists to handle the edge case where the context window file was written by a PREVIOUS session and no longer reflects the current agent state.

## Proposed Simplification
Simplify to a 2-case model:
- **File present** → trust the value (remainingPercentage is non-null)
- **File missing** → treat as unknown, do not compact

## Why This Is Simpler AND More Robust
- **Health monitoring already catches stale agents**: If an agent stalls and stops updating the context file, the no-activity timeout (30 min) in health monitoring will detect and handle it. Staleness detection is redundant defense.
- **Session rotation clears state**: When a session is rotated (compaction), the old context file is naturally replaced. The new session writes its own file.
- **Non-nullable remainingPercentage**: Every consumer can work with a simple `Int` instead of `Int?`, removing null-handling branches.
- **Fewer configuration parameters**: No `contextFileStaleTimeout` to tune or get wrong.
- **Simpler ContextWindowStateReader**: Just read file → parse → return value, or return Missing.

## Affected Specs
- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E)
- `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — ContextWindowStateReader interface (ref.ap.ufavF1Ztk6vm74dLAgANY.E)

