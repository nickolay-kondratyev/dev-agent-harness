---
id: nid_agxosk4u7c6kwe7eoi9ke32rw_E
title: "SIMPLIFY_CANDIDATE: Unify soft/hard context window compaction into single flow"
status: in_progress
deps: []
links: []
created_iso: 2026-03-15T01:03:01Z
status_updated_iso: 2026-03-17T20:49:28Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, compaction]
---

The ContextWindowSelfCompactionUseCase (doc/use-case/ContextWindowSelfCompactionUseCase.md) defines two separate compaction flows:

1. **Flow 1 (Soft)**: Triggered at done boundary when ≤35% context remaining. Sends compaction instruction, awaits SelfCompacted, validates PRIVATE.md, rotates session.
2. **Flow 2 (Hard)**: Emergency interrupt at ≤20% remaining via Ctrl+C. Sends compaction instruction, awaits SelfCompacted, validates PRIVATE.md, kills session, respawns, sends instructions, resumes.

## Observation
Both flows perform the SAME core operation: instruct agent to compact → await SelfCompacted signal → validate PRIVATE.md → rotate session. The only difference is the entry trigger (done boundary vs emergency interrupt + Ctrl+C).

## Proposal
Extract a single `performCompaction(session, trigger: CompactionTrigger)` flow. The two triggers just set up preconditions differently:
- Soft: no interrupt needed, agent is already between tasks
- Hard: send Ctrl+C first, then enter the same compaction flow

## Benefits
- SIMPLER: One compaction flow instead of two that could diverge over time
- MORE ROBUST: Single well-tested path; changes to compaction logic only need to happen once; eliminates risk of the two flows drifting apart
- Easier to test: one flow with two entry points vs two separate flows

## Affected Specs
- doc/use-case/ContextWindowSelfCompactionUseCase.md (Flow 1 and Flow 2)
- doc/core/PartExecutor.md (health-aware await loop references both flows)

## Risk
- Low: The core compaction logic is identical. The pre-compaction steps (interrupt vs no-op) remain distinct and small.

