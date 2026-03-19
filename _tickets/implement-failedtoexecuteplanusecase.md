---
id: nid_foubbnsh3vmk1fk34zm75zkg0_E
title: "Implement FailedToExecutePlanUseCase"
status: in_progress
deps: [nid_smb6zudqraf0hkp3u9kjx855e_E]
links: []
created_iso: 2026-03-18T17:37:08Z
status_updated_iso: 2026-03-19T13:30:03Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [health-monitoring, use-case]
---

## Context

Spec: `doc/use-case/HealthMonitoring.md` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E), section "FailedToExecutePlanUseCase Detail" (lines 187-210).

When plan execution hits a blocking failure (`FailedWorkflow`, `AgentCrashed`, or `FailedToConverge` where user chose to abort), this UseCase performs deterministic cleanup and exits.

## What to Implement

### 1. FailedToExecutePlanUseCase interface + implementation
```kotlin
interface FailedToExecutePlanUseCase {
    suspend fun handleFailure(failedResult: PartResult): Nothing
}
```

### 2. Behavior (4 steps, always in order)
1. **Print failure reason in red** to console — formatted per `PartResult` variant (receives full sealed class, not just string)
2. **Kill all TMUX sessions immediately** — debug artifacts already persisted in `.ai_out/` and git history
3. **Record failure learning (best-effort)** — invoke `TicketFailureLearningUseCase` (ref.ap.cI3odkAZACqDst82HtxKa.E). If this step fails (LLM error, git error), log WARN and continue. Learning is best-effort.
4. **Exit with non-zero exit code**

### 3. Key Design Decisions (from spec)
- No stdin blocking. No interactive prompt. Deterministic cleanup.
- Always kills sessions, always exits.
- No automated agent spawning, no git rollback.
- The only "smart" behavior is step 3 (ticket failure learning), and even that is non-fatal.
- V2 may add automated cleanup via CLEANUP_AGENT.

### 4. Dependencies
- `PartResult` sealed class (variants: `Completed`, `FailedWorkflow`, `FailedToConverge`, `AgentCrashed`)
  - NOTE: PartResult sealed class may not exist yet — define it as part of this ticket or depend on PartExecutor ticket.
- `TicketFailureLearningUseCase` interface (for best-effort learning call)
  - If TicketFailureLearningUseCase is not yet implemented, depend on the INTERFACE only (can use a no-op stub)
- TMUX session manager (to kill all sessions)
- Out logger (structured logging)

### 5. Unit Tests (BDD/DescribeSpec)
- GIVEN FailedWorkflow result WHEN handleFailure called THEN red error printed with workflow details AND sessions killed AND exit non-zero
- GIVEN AgentCrashed result WHEN handleFailure called THEN red error printed with crash details AND sessions killed AND exit non-zero
- GIVEN FailedToConverge result WHEN handleFailure called THEN red error printed AND sessions killed AND exit non-zero
- GIVEN TicketFailureLearningUseCase throws WHEN handleFailure called THEN warning logged AND still exits non-zero (learning is non-fatal)

## Package
`com.glassthought.shepherd.usecase.healthmonitoring`

## Spec References
- Lines 187-210 of doc/use-case/HealthMonitoring.md
- TicketFailureLearningUseCase: doc/use-case/TicketFailureLearningUseCase.md (ref.ap.cI3odkAZACqDst82HtxKa.E)

## Acceptance Criteria
- FailedToExecutePlanUseCase interface + implementation
- All 4 steps executed in order for all PartResult failure variants
- TicketFailureLearningUseCase failure is non-fatal (logged, not thrown)
- Unit tests cover all PartResult variants + learning failure case


## Notes

**2026-03-19T00:58:01Z**

Added dep on nid_smb6zudqraf0hkp3u9kjx855e_E (SubPartStatus/SubPartStateTransition). PartResult should be defined in that ticket cohesively with other state types, NOT ad-hoc here.
