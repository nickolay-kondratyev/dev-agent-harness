---
closed_iso: 2026-03-17T22:45:35Z
id: nid_ysgpuu6eo09h0skpvrbwfefok_E
title: "SIMPLIFY_CANDIDATE: Make SubPart state transitions explicit via SubPartStateTransition enum — eliminate scattered implicit transition validation"
status: closed
deps: []
links: []
created_iso: 2026-03-17T22:15:45Z
status_updated_iso: 2026-03-17T22:45:35Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, state-machine, executor]
---

The spec (doc/schema/plan-and-current-state.md) defines 5 valid SubPartStatus transitions:
  NOT_STARTED → IN_PROGRESS (spawn)
  IN_PROGRESS → COMPLETED   (doer: done, reviewer: pass)
  IN_PROGRESS → FAILED      (fail-workflow / crash)
  IN_PROGRESS → IN_PROGRESS (reviewer: needs_iteration — counter increments)
  COMPLETED   → IN_PROGRESS (doer resuming after reviewer requested iteration)

Problem:
- These rules are implicit, scattered across PartExecutor signal handling and iteration logic.
- No single authoritative source for "which transitions are legal from this state."
- Silent acceptance of invalid transitions possible if new code paths forget to validate.
- Extending the state machine (new status, new trigger) requires hunting down all the validation sites.

Proposed simplification:
- Create `SubPartStateTransition` sealed class/enum covering SPAWN, COMPLETE, FAIL, ITERATE_CONTINUE, RESUME_FOR_ITERATION.
- Validator: `fun SubPartStatus.transitionTo(trigger: AgentSignal): SubPartStateTransition` — throws on invalid transition.
- PartExecutor calls validator before updating status — no status mutation without validated transition.
- The enum KDoc IS the state machine diagram.

Robustness improvement:
- Invalid transitions are caught at the transition site, not silently accepted.
- State machine is auditable in one place.
- Adding new states/transitions is additive — update enum + when branch, compiler enforces exhaustiveness.
- Simpler testing: unit test the validator with all (state, signal) pairs.

Relevant specs: doc/schema/plan-and-current-state.md (SubPartStatus section), doc/core/PartExecutor.md


## Notes

**2026-03-17T22:45:31Z**

Completed spec-only update. Added SubPartStateTransition sealed class (ap.EHY557yZ39aJ0lV00gPGF.E) to doc/schema/plan-and-current-state.md with 5 transitions (Spawn, Complete, Fail, IterateContinue, ResumeForIteration) and 3 validators (transitionTo(AgentSignal), validateCanSpawn(), validateCanResumeForIteration()). Updated doc/core/PartExecutor.md with Status Mutation Protocol table. Committed as 83265f7.
