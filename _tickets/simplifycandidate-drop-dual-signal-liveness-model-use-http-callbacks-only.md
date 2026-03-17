---
id: nid_s6jsgj1f3zuifzyh726d7su95_E
title: "SIMPLIFY_CANDIDATE: Drop dual-signal liveness model — use HTTP callbacks only"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:08:05Z
status_updated_iso: 2026-03-15T01:08:05Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, health-monitoring]
---

The health monitoring spec (doc/use-case/HealthMonitoring.md) uses a dual-signal liveness model:
1. HTTP callback timestamps from agent → server
2. External context_window_slim.json file polling via an external hook

The dual model creates a 4-case decision matrix (fresh/stale × fresh/stale) and depends on an external hook that is outside the harness's control. If the hook is misconfigured or missing, the system degrades non-obviously.

Proposal: Use HTTP callback timestamps only for liveness detection.
- If agent is alive and working, it sends HTTP callbacks (signals, queries, ACKs).
- If agent is dead, callbacks stop → timeout fires → health UseCase kicks in.
- Decision matrix drops from 4 cases to 2 (fresh callback / stale callback).
- Removes fragile external dependency on context_window_slim.json hook.
- The context_window_slim.json can still be used for context window COMPACTION decisions (its original purpose), but NOT for liveness.

Files affected:
- doc/use-case/HealthMonitoring.md (simplify decision matrix)
- doc/core/PartExecutor.md (simplify health-aware await loop)
- Implementation of health monitoring UseCases (ref.ap.RJWVLgUGjO5zAwupNLhA0.E)

