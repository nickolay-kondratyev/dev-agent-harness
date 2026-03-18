---
id: nid_bmf7mq2lrusqiibmubwtrvyy0_E
title: "SIMPLIFY_CANDIDATE: Defer AutoRecoveryByAgentUseCase to V2 — fail-fast on non-index.lock git failures"
status: in_progress
deps: []
links: [nid_o9z2nk2kwqrxwa6g12rlyrihr_E]
created_iso: 2026-03-18T14:58:24Z
status_updated_iso: 2026-03-18T15:30:00Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, recovery, v1-scope]
---

## Problem
AutoRecoveryByAgentUseCase (doc/use-case/AutoRecoveryByAgentUseCase.md, ref.ap.q54vAxzZnmWHuumhIQQWt.E) spawns a PI agent via NonInteractiveAgentRunner with a 20-minute timeout to attempt recovery from infrastructure failures. In V1, the only consumer is git operation failure handling (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E).

This adds:
- AutoRecoveryByAgentUseCase class + RecoveryRequest/RecoveryOutcome types
- PI agent type support (AgentTypeAdapter for PI in NonInteractiveAgentRunner)
- 20-minute timeout that delays failure reporting
- Recovery agent instruction assembly
- Recovery agent failure handling (what if recovery itself fails?)

## Proposal
In V1, keep ONLY the fast-path index.lock detection + delete (trivial, handles the most common git failure). All other git failures → immediate FailedToExecutePlanUseCase with clear error message.

Defer agent-based recovery to V2.

## Why More Robust
- Fail-fast is MORE robust than hoping a recovery agent can fix infrastructure issues
- Recovery agents have their own failure modes (agent crash, timeout, wrong fix)
- 20-min timeout means 20 minutes of waiting before the user even knows something is wrong
- Index.lock fast-path handles ~80% of git failures (Pareto)
- Clear error message lets human diagnose and fix the actual issue

## What Gets Eliminated
- AutoRecoveryByAgentUseCase class
- RecoveryRequest / RecoveryOutcome types
- PI agent support in NonInteractiveAgentRunner (if TicketFailureLearning also simplified)
- Recovery instruction template
- Recovery-specific error handling

## Affected Specs
- doc/use-case/AutoRecoveryByAgentUseCase.md (archive or mark V2)
- doc/core/git.md (simplify GitOperationFailureUseCase to index.lock + fail-fast)
- doc/core/NonInteractiveAgentRunner.md (remove PI consumer if TicketFailureLearning also deferred)

