---
id: nid_3htabsnb3hsz27p7l26jrrskc_E
title: "SIMPLIFY_CANDIDATE: Replace AutoRecoveryByAgentUseCase's open-ended recovery loop with a fixed MaxRecoveryAttempts cap plus structured RecoveryOutcome"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T22:16:00Z
status_updated_iso: 2026-03-17T22:29:16Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, auto-recovery, error-handling]
---

AutoRecoveryByAgentUseCase (doc/use-case/AutoRecoveryByAgentUseCase.md, ref.ap.q54vAxzZnmWHuumhIQQWt.E) is a generic recovery mechanism that spawns a recovery agent when the harness detects a problem.

Problem:
- Recovery is described as "spawn recovery agent, await result, re-enter normal flow" with unclear loop bounds.
- No explicit cap on how many times recovery can be attempted before giving up and failing the ticket.
- No structured RecoveryOutcome type — success/failure/needs-escalation paths are implicit.
- Risk: recovery could loop indefinitely if the recovery agent keeps returning "fixed" but the underlying issue persists.

Proposed simplification:
- Add `maxRecoveryAttempts: Int` (default 2) to the use case configuration.
- Define `sealed class RecoveryOutcome { object Resolved; object Unresolvable; data class NeedsEscalation(val reason: String) }`.
- After `maxRecoveryAttempts` failures, emit FAIL signal with reason "recovery_exhausted" — no silent infinite loop.
- RecoveryOutcome is returned by the use case and drives the caller's next action (resume, fail, escalate).\n\nRobustness improvement:\n- Bounded retries prevent infinite recovery loops masking a permanent defect.\n- Explicit RecoveryOutcome makes all exit paths visible to callers (no implicit "just try again").\n- Easier to test: inject mock agent that always fails, verify FAIL emitted after N attempts.\n- Aligns with TicketFailureLearningUseCase (ref.ap.cI3odkAZACqDst82HtxKa.E) which also caps retries.\n\nRelevant specs: doc/use-case/AutoRecoveryByAgentUseCase.md, doc/use-case/TicketFailureLearningUseCase.md

