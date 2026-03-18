---
id: nid_fq8wn0eb9yrvzcpzdurlmsg7i_E
title: "Implement Inner Feedback Loop in PartExecutorImpl"
status: open
deps: [nid_dnelaf98097nicijp4kvjfd1d_E, nid_rnusi51qg9yw7cmszkes0l1ab_E, nid_92vpmdxcn3j8f98gzgu9eln43_E, nid_gp9rduvxoqf14m95z9bttnaxq_E, nid_fjod8du6esers3ajur2h7tvgx_E]
links: []
created_iso: 2026-03-18T22:31:11Z
status_updated_iso: 2026-03-18T22:31:11Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [granular-feedback-loop, part-executor, inner-loop]
---

Implement the granular inner feedback loop in `PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E).
Part Completion Guard (R8) split to nid_yzmwosyazxksnr1hafmw87x1m_E.

## Context
Spec: `doc/plan/granular-feedback-loop.md` (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).
This is the core orchestration change that ties all feedback loop components together.

## Requirements Covered
- **R3**: Inner Feedback Loop — after reviewer `needs_iteration`, list files in `pending/`, process in severity order (critical → important → optional, sorted by filename within severity). Feed ONE feedback file at a time to doer via re-instruction.
- **R8**: ~~Split to nid_yzmwosyazxksnr1hafmw87x1m_E~~
- **R9**: Feedback Files Presence Guard — after `needs_iteration`, validate at least one file exists in `pending/`. If empty → `PartResult.AgentCrashed`.
- **R10**: Iteration Counter Unchanged — `iteration.current` increments once per reviewer `needs_iteration`, NOT per individual feedback item.
- **R11**: Harness-Owned File Movement — after doer `done` + resolution marker read: ADDRESSED → move to `addressed/`, REJECTED → delegate to `RejectionNegotiationUseCase`.

## Flow (step 4 extension in PartExecutorImpl)
```
4. On reviewer NEEDS_ITERATION:
   a. PUBLIC.md validation (existing)
   b. Budget check (existing)
   d. GitCommitStrategy.onSubPartDone (existing)
   e. Increment iteration.current (existing)
   e2. FEEDBACK FILES PRESENCE GUARD
       Empty pending/ → PartResult.AgentCrashed
   f. INNER FEEDBACK LOOP
      ├─ critical__* files (sorted) → PROCESS_FEEDBACK_ITEM each
      ├─ important__* files (sorted) → PROCESS_FEEDBACK_ITEM each
      └─ optional__* files (sorted) → PROCESS_FEEDBACK_ITEM each (with skip guidance)
   g. Validate: pending/ has no critical__*/important__* files (bug guard)
   h. Assemble reviewer instructions (includes addressed/ + rejected/ contents)
   i. Deliver to reviewer
   j. Await reviewer → step 3 (PASS) or step 4 (NEEDS_ITERATION)
```

PROCESS_FEEDBACK_ITEM:
```
├─ Self-compaction check at done boundary
├─ Assemble doer instructions (single feedback item)
├─ ReInstructAndAwait.execute(doerHandle, instructions)
│   └─ On Crashed/FailedWorkflow → propagate
├─ PUBLIC.md validation (shallow)
├─ Read ## Resolution marker
│   ├─ Missing → PartResult.AgentCrashed
│   ├─ ADDRESSED → move to addressed/, git commit
│   ├─ SKIPPED (optional only) → move to addressed/, git commit
│   └─ REJECTED → RejectionNegotiationUseCase.execute()
└─ (next file)
```

## Dependencies
- `ReInstructAndAwait` (ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E) — for per-item doer delivery
- `RejectionNegotiationUseCase` (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) — for rejection handling
- `FeedbackResolutionParser` — for reading resolution markers
- Feedback-loop InstructionSection subtypes (nid_gp9rduvxoqf14m95z9bttnaxq_E) — for doer per-item instruction assembly
- `AiOutputStructure.ensureStructure()` (nid_fjod8du6esers3ajur2h7tvgx_E) — `__feedback/` dirs exist
- `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) — assembles per-item + reviewer instructions

## Testing (via FakeAgentFacade + virtual time)
- Unit test: inner loop processes critical → important → optional in order
- Unit test: ADDRESSED → file moved to addressed/
- Unit test: SKIPPED on optional → file moved to addressed/
- Unit test: missing resolution marker → immediate AgentCrashed
- Unit test: SKIPPED on critical/important → AgentCrashed
- Unit test: self-compaction check fires at each done boundary
- Unit test: iteration.current increments once per needs_iteration (not per item)
- Unit test: needs_iteration with empty pending → immediate AgentCrashed
- (R8 PASS guard tests moved to nid_yzmwosyazxksnr1hafmw87x1m_E)
- Unit test: rejection → delegates to RejectionNegotiationUseCase
- Unit test: multiple items across all severities processed in correct order

## Package
`com.glassthought.shepherd.core` (existing PartExecutorImpl location)

## Acceptance Criteria
- All unit tests pass
- Full inner loop flow works with FakeAgentFacade
- (Part completion guard split to nid_yzmwosyazxksnr1hafmw87x1m_E)
- Feedback files presence guard catches empty pending on needs_iteration
- Iteration counter unchanged by inner loop processing
- Harness moves files, never agents


## Notes

**2026-03-18T22:33:46Z**

## Spec Docs to Update During Implementation
- Update `doc/core/PartExecutor.md` with inner feedback loop changes
- Update `doc/high-level.md` sub-part transitions section
- Update `doc/use-case/ContextWindowSelfCompactionUseCase.md` with inner loop done-boundary source note

**2026-03-18T22:53:25Z**

Split out Part Completion Guard (R8, Gate 5) into separate ticket nid_yzmwosyazxksnr1hafmw87x1m_E to keep this ticket focused on the inner loop orchestration (R3, R9, R10, R11). This ticket no longer covers R8 — the PASS branch guard.

**2026-03-18T23:14:18Z**

Spec doc updates (PartExecutor.md, high-level.md, ContextWindowSelfCompactionUseCase.md, etc.) are tracked in a separate ticket: nid_i5se9ful3qgnq1s0ccifoauve_E. Do NOT update spec docs in this ticket — focus on implementation and tests only.
