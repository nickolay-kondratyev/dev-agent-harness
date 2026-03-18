---
id: nid_tk6as1efs9eb8htwomz33boqq_E
title: "SIMPLIFY_CANDIDATE: Remove NeedsEscalation from RecoveryOutcome — collapse into Unresolvable"
status: open
deps: []
links: []
created_iso: 2026-03-17T23:54:00Z
status_updated_iso: 2026-03-17T23:54:00Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec-change]
---

In `doc/use-case/AutoRecoveryByAgentUseCase.md` (ref.ap.q54vAxzZnmWHuumhIQQWt.E), `RecoveryOutcome` has three variants: `Resolved`, `Unresolvable`, and `NeedsEscalation(reason)`.

**Problem**: `NeedsEscalation` and `Unresolvable` result in the identical caller behavior — delegate to `FailedToExecutePlanUseCase` (prints red error, halts). The only difference is the reason string. Having two variants that lead to the same outcome:
- Forces callers to handle two branches that do the same thing
- Adds a sealed class variant and its associated matching boilerplate
- Creates an unnecessary distinction: "agent couldn't fix it" vs "agent says human must fix it" — both result in human intervention

**Simplification**: Collapse `NeedsEscalation(reason)` into `Unresolvable(reason: String)`. The `Unresolvable` variant gains an optional reason field. Callers have a simple binary outcome: `Resolved` → retry, `Unresolvable` → halt.

**What changes**:
- `doc/use-case/AutoRecoveryByAgentUseCase.md` — `RecoveryOutcome` becomes two variants: `Resolved` and `Unresolvable(reason: String)`. Remove NeedsEscalation documentation. Update caller protocol section.
- When the recovery agent signals escalation, the use case returns `Unresolvable(reason)` immediately (same early-exit behavior as NeedsEscalation had).

**Robustness**: Unchanged — both variants already lead to the same halt behavior. The simplification removes a distinction without a difference.

