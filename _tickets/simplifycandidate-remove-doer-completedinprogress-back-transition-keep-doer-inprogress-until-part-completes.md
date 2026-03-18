---
closed_iso: 2026-03-18T13:54:27Z
id: nid_zm78ycz9pqfty7rzg066dwgyx_E
title: "SIMPLIFY_CANDIDATE: Remove doer COMPLETED→IN_PROGRESS back-transition — keep doer IN_PROGRESS until part completes"
status: closed
deps: []
links: []
created_iso: 2026-03-18T00:07:43Z
status_updated_iso: 2026-03-18T13:54:27Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, spec-change]
---

## Current Design (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E, ref.ap.EHY557yZ39aJ0lV00gPGF.E)

SubPartStatus state transitions include a back-transition for the doer:

```
NOT_STARTED → IN_PROGRESS    (harness spawns agent)
IN_PROGRESS → COMPLETED      (doer: "completed", reviewer: "pass")
IN_PROGRESS → FAILED         (fail-workflow, agent crash, failed-to-converge)
IN_PROGRESS → IN_PROGRESS    (reviewer: "needs_iteration" — counter increments, status stays)
COMPLETED   → IN_PROGRESS    (doer re-iteration: reviewer signals "needs_iteration")
```

The COMPLETED → IN_PROGRESS transition fires when the reviewer signals `needs_iteration`. The doer, which was marked COMPLETED, must go back to IN_PROGRESS for re-work.

## Problem

This is the ONLY back-transition in the state machine. It means:
- `SubPartStateTransition` must include `validateCanResumeForIteration()` — a unique validator for this single case
- The `when` expression over transitions has an extra branch for back-transitions
- "COMPLETED" for the doer is misleading — it means "completed this round" not "done forever"
- State machine is no longer strictly forward-only, making reasoning harder

## Proposed Simplification

Don't mark doer as COMPLETED until the entire part completes. During iteration cycles, doer stays IN_PROGRESS.

```
NOT_STARTED → IN_PROGRESS    (harness spawns agent)
IN_PROGRESS → COMPLETED      (part completes — reviewer PASS or doer-only done)
IN_PROGRESS → FAILED         (fail-workflow, agent crash, failed-to-converge)
```

No back-transitions. Strictly forward-only state machine.

Doer's per-round completion is an internal event (agentSignal.Done with result=COMPLETED), but does NOT change SubPartStatus. The iteration.current counter on the reviewer already tracks where we are in the cycle.

## Why This Is Both Simpler AND More Robust

- Eliminates the only back-transition — state machine is strictly forward-only
- Removes `validateCanResumeForIteration()` validator
- COMPLETED means COMPLETED (not "completed this round, might reopen")
- Fewer `SubPartStateTransition` branches — compiler-enforced exhaustiveness still works
- `iteration.current` on reviewer already tracks progress — doer status was redundant signal
- No information loss: git commits after each doer done signal preserve the per-round history

## Spec Files to Update

- `doc/core/PartExecutor.md` (state transitions table, SubPartStateTransition validators)
- `doc/schema/plan-and-current-state.md` (SubPartStatus transitions)
- `doc/high-level.md` (if state transitions are summarized there)


## Notes

**2026-03-18T13:54:40Z**

Spec updated successfully. Changes made to doc/schema/plan-and-current-state.md and doc/core/PartExecutor.md:

1. Removed COMPLETED → IN_PROGRESS back-transition from SubPartStatus state machine — now strictly forward-only.
2. Removed SubPartStateTransition.ResumeForIteration entry from sealed class.
3. Removed validateCanResumeForIteration() validator function.
4. Updated Complete KDoc to describe doer-in-doer+reviewer completion triggered by executor on reviewer PASS.
5. Updated transitionTo() KDoc with note about doer+reviewer semantics.
6. Validator count updated: Three/five → Two/four.
7. COMPLETED error message now says 'terminal — no further transitions allowed' (removed stale ref to validateCanResumeForIteration).
8. Both current_state.json examples updated: doer/planner status changed from COMPLETED to IN_PROGRESS during mid-iteration state.
9. Updated example comments to explain the new semantics.
10. Updated persistence timing table row for 'Agent signals done'.
11. PartExecutor.md flow step 2 notes doer stays IN_PROGRESS; step 3 notes executor marks both COMPLETED on reviewer PASS.
12. Status Mutation Protocol table: removed COMPLETED→IN_PROGRESS row, added doer part-completion row.
