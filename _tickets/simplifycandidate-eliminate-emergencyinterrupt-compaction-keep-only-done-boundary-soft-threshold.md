---
id: nid_erxsg4rjc8hpcflkg7ufbh9k9_E
title: "SIMPLIFY_CANDIDATE: Eliminate EMERGENCY_INTERRUPT compaction — keep only done-boundary soft threshold"
status: open
deps: []
links: []
created_iso: 2026-03-18T00:06:56Z
status_updated_iso: 2026-03-18T00:06:56Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, spec-change]
---

FEEDBACK:
--------------------------------------------------------------------------------
## Current Design (ref.ap.8nwz2AHf503xwq8fKuLcl.E)

Two context window compaction thresholds:
- **Soft (35%)**: Checked at done boundaries. Non-urgent: session killed after done, fresh session spawned lazily.
- **Hard (20%)**: Continuous 1-second polling. Emergency: Ctrl+C interrupt mid-task, forced compaction, immediate respawn.

The EMERGENCY_INTERRUPT path is the most complex code path in the system:
- Race condition guard (done signal vs Ctrl+C timing)
- Interrupt acknowledgment prefix in compaction instructions
- Ctrl+C via TMUX `sendRawKeys()` — agent may be mid-tool-use
- File corruption risk from interrupted writes
- Git commit before compaction to capture last-known-good state
- Immediate respawn with PRIVATE.md

## Problem

The hard threshold path adds disproportionate complexity for a rare scenario. The soft threshold at done boundaries already covers the common case. With the granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E), done boundaries are frequent (one per feedback item). An agent burning from 35% → 0% remaining in a single uninterrupted stretch is unlikely for typical tasks.

## Proposed Simplification

Keep only the soft threshold at done boundaries. Remove EMERGENCY_INTERRUPT entirely.

- Remove `SELF_COMPACTION_HARD_THRESHOLD` config
- Remove `CompactionTrigger.EMERGENCY_INTERRUPT` enum value
- Remove Ctrl+C interrupt logic from `performCompaction`
- Remove continuous context window polling from health-aware await loop (keep liveness polling)
- Remove race condition guard between done signal and interrupt
- Remove interrupt acknowledgment prefix in compaction instructions

## Why This Is Both Simpler AND More Robust

- Eliminates the most complex, hardest-to-test code path
- Removes Ctrl+C timing risks (file corruption, interrupted tool use)
- Removes race condition between done signal and interrupt
- Soft threshold at 35% is generous — 70K tokens remaining on a 200K window
- Granular feedback loop creates frequent done boundaries, reducing risk of context exhaustion between checks
- If context IS exhausted, agent session dies — harness detects via health monitoring and returns AgentCrashed (existing mechanism)

## Risk Assessment

The only risk: agent uses 35% → 0% between done signals. Mitigated by:
1. Granular feedback loop creates frequent done boundaries
2. Health monitoring catches dead sessions
3. V1 has serial execution — only one agent active, context usage is predictable

## Spec Files to Update

- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (major changes — remove hard threshold, emergency interrupt flow, race guards)
- `doc/high-level.md` (context window section)
- `doc/core/PartExecutor.md` (health-aware await loop references)
- `doc/plan/granular-feedback-loop.md` (emergency interrupt reference)

--------------------------------------------------------------------------------
<DECISION>
DECISION: It's not that rare of the scenario and if we do not have it we put ourselves at RISK of being STUCK with an agent in ran out of contexts state that is not able to compress. Alternative for V1: do not implement our own emergency compression and rely on claude codes auto compression. Which is reasonable simplification. Let's do that. Make sure to look at all the specs in under FOLDER=[./doc]. FOCUS on specs not code. To move all the CTRL+C interruption into [./doc_v2/our-own-emergency-compression.md] this will allow us to SIMPLIFY all the specs in regard to emergency compression in V1 for claude code, and simplify the configuration for turning off compression. We will want to move the setting for auto-compression OFF into [./doc_v2/our-own-emergency-compression.md] and point to ./doc_v2/our-own-emergency-compression.md from main specs.
</DECISION>