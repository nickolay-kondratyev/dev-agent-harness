---
closed_iso: 2026-03-18T15:36:23Z
id: nid_bmf7mq2lrusqiibmubwtrvyy0_E
title: "SIMPLIFY_CANDIDATE: Defer AutoRecoveryByAgentUseCase to V2 — fail-fast on non-index.lock git failures"
status: closed
deps: []
links: [nid_o9z2nk2kwqrxwa6g12rlyrihr_E]
created_iso: 2026-03-18T14:58:24Z
status_updated_iso: 2026-03-18T15:36:23Z
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

## Resolution

**Completed.** All spec changes applied:

1. **`doc/use-case/AutoRecoveryByAgentUseCase.md`** — Marked V2 DEFERRED with clear banner. V2 design content preserved under "V2 Design" section for future implementation. Removed V1-specific caller protocol, flow details, and "Not a Retry Mechanism" sections.

2. **`doc/core/git.md`** — `GitOperationFailureUseCase` (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E) simplified:
   - Section renamed from "Git Operation Failure Handling — AutoRecoveryByAgentUseCase" to "Git Operation Failure Handling"
   - Kept index.lock fast-path (deterministic delete + retry)
   - Replaced "Standard agent recovery path" with fail-fast to `FailedToExecutePlanUseCase`
   - Updated failure points table and Git Operations Summary table

3. **`doc/core/NonInteractiveAgentRunner.md`** — Removed `AutoRecoveryByAgentUseCase` from consumers table. Added V1 note that PI consumer is deferred to V2. PI command construction retained for V2 readiness.

4. **`doc/high-level.md`** — Updated git failure handling bullet (line 481) and linked documentation table entry for AutoRecoveryByAgentUseCase.

5. **`ai_input/memory/auto_load/1_core_description.md`** + **`CLAUDE.md`** (regenerated) — Marked AutoRecoveryByAgentUseCase as "V2 — deferred" in spec reference table.

**Verified**: All 20 remaining spec files reviewed — no stale references to AutoRecoveryByAgentUseCase in V1 flow paths.

