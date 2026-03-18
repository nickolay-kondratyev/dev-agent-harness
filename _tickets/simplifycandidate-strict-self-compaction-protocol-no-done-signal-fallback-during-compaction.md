---
id: nid_kz0l9iajekpnfyfc2bnk5lnsd_E
title: "SIMPLIFY_CANDIDATE: Strict self-compaction protocol — no done-signal fallback during compaction"
status: open
deps: []
links: []
created_iso: 2026-03-18T02:23:14Z
status_updated_iso: 2026-03-18T02:23:14Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, compaction, protocol, robustness]
---

## Problem

The self-compaction spec (ref.ap.8nwz2AHf503xwq8fKuLcl.E) defines a fallback:
- If an agent signals `done` instead of `self-compacted` during compaction, the harness checks if PRIVATE.md exists
- If PRIVATE.md exists → proceed as if compaction succeeded
- If PRIVATE.md missing → re-instruct

This fallback creates:
- An ambiguous signal interpretation ("done" means different things depending on context)
- A conditional branch that mixes two protocol flows
- Silent acceptance of protocol violations (agent didn't follow instructions but happened to produce the right artifact)\n- Potential for protocol drift — agents learn they can signal `done` instead of `self-compacted` without consequence\n\n## Proposed Simplification\n\nStrict protocol enforcement:\n- During compaction, only `AgentSignal.SelfCompacted` is the success signal\n- If `done` received during compaction → re-instruct once (\"you are in compaction mode, signal self-compacted\")\n- If `done` received again → `AgentCrashed` (agent cannot follow protocol)\n- No PRIVATE.md existence check as fallback logic\n\n## What Gets Removed\n- The \"done-during-compaction-but-PRIVATE.md-exists\" conditional branch\n- PRIVATE.md existence check in the compaction flow (still validated after `self-compacted`)\n- Ambiguous signal interpretation logic\n\n## Why This Is Also MORE Robust\n- Clear protocol contract: compaction → `self-compacted`, work → `done`\n- No silent protocol drift\n- If an agent can't produce the correct signal, it genuinely needs re-instruction (which is handled)\n- Simpler state machine in the health-aware await loop during compaction\n- Existing `ReInstructAndAwait` pattern handles the retry cleanly\n\n## Specs Affected\n- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (primary)

