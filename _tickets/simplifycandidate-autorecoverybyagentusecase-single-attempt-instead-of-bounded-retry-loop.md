---
id: nid_f1gdpwvkohsyykfgfd7qx3g9h_E
title: "SIMPLIFY_CANDIDATE: AutoRecoveryByAgentUseCase — single attempt instead of bounded retry loop"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T23:54:16Z
status_updated_iso: 2026-03-18T13:23:40Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec-change]
---

In `doc/use-case/AutoRecoveryByAgentUseCase.md` (ref.ap.q54vAxzZnmWHuumhIQQWt.E), the use case has an internal bounded retry loop of `maxRecoveryAttempts` (default 2) — spawning the recovery agent up to 2 times per `attemptRecovery()` call.

**Problem**: The internal retry loop adds:
- A `maxRecoveryAttempts` constructor parameter
- A loop with failure counting logic
- Multiple agent spawns for a single failure (up to 2 PI agents × 20 min timeout = 40 min worst-case before giving up)
- Conceptual complexity: is this the same recovery agent retrying, or a fresh attempt? (It's a fresh spawn each time)\n\n**The key insight**: If a recovery agent (a code agent with full file access and the explicit error context) cannot fix an infrastructure issue in one attempt, the issue is almost certainly too complex for automated recovery. The second attempt receives the same context and has no reason to succeed where the first failed — unless the first attempt partially fixed the issue, which is unpredictable and not something the retry loop is designed to handle.\n\n**Simplification**: Attempt recovery exactly once. Return `Resolved` on success, `Unresolvable` on any failure.\n\n**What changes**:\n- `doc/use-case/AutoRecoveryByAgentUseCase.md` — remove the `maxRecoveryAttempts` parameter, remove the internal loop, simplify the flow to: run agent → Success/Failed/TimedOut → Resolved/Unresolvable\n- The interface stays the same (minus `maxRecoveryAttempts` constructor note)\n- Callers unchanged — they already handle Resolved/Unresolvable\n\n**Robustness improvement**: Reduces worst-case recovery time from 40 minutes to 20 minutes before escalating to human intervention. Faster escalation for genuinely unrecoverable issues means less wasted time. The single attempt still catches the common case (e.g., stale index.lock already handled by fast-path in `GitOperationFailureUseCase`).

DECISION: Yes lets SIMPLIFY this to have single attempt at recovery.