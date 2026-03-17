---
closed_iso: 2026-03-17T22:32:36Z
id: nid_3htabsnb3hsz27p7l26jrrskc_E
title: "SIMPLIFY_CANDIDATE: Replace AutoRecoveryByAgentUseCase's open-ended recovery loop with a fixed MaxRecoveryAttempts cap plus structured RecoveryOutcome"
status: closed
deps: []
links: []
created_iso: 2026-03-17T22:16:00Z
status_updated_iso: 2026-03-17T22:32:36Z
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


## Notes

**2026-03-17T22:32:25Z**

Completed spec-only update to doc/use-case/AutoRecoveryByAgentUseCase.md.

Changes made:
- Replaced RecoveryResult sealed class with RecoveryOutcome (Resolved, Unresolvable, NeedsEscalation)
- Updated interface return type: attemptRecovery() now returns RecoveryOutcome
- Added maxRecoveryAttempts (default 2) as constructor config parameter on the use case
- Flow section now shows bounded internal retry loop (up to maxRecoveryAttempts)
- Unresolvable returned with reason "recovery_exhausted" after all attempts exhausted
- NeedsEscalation returned immediately when agent signals escalation (without consuming further attempts)
- Caller Protocol updated: callers handle Resolved/Unresolvable/NeedsEscalation outcomes explicitly
- Removed "Single recovery attempt" paragraph; replaced with "Bounded recovery" explanation
