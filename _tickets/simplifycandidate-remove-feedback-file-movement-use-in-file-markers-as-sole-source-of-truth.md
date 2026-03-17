---
closed_iso: 2026-03-17T20:34:28Z
id: nid_42opcvts7o5uhqux83dqoh9t1_E
title: "SIMPLIFY_CANDIDATE: Remove feedback file movement — use in-file markers as sole source of truth"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:08:44Z
status_updated_iso: 2026-03-17T20:34:28Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, feedback, file-management]
---

FEEDBACK
--------------------------------------------------------------------------------
The granular feedback loop spec (doc/plan/granular-feedback-loop.md) uses harness-owned file movement:
- Reviewer writes feedback items to __feedback/pending/ with severity-prefixed filenames
- Doer writes ## Resolution: ADDRESSED/REJECTED marker in the file
- Harness detects marker and MOVES file from pending/ to addressed/ or rejected/
- Harness tracks state via directory location

The ## Resolution marker is already the source of truth — the file movement is redundant bookkeeping that duplicates this information in the filesystem structure.

Proposal: Keep files in place, use markers only.
- Reviewer writes feedback items to __feedback/ (flat, no pending/ subdirectory)
- Doer writes ## Resolution: ADDRESSED or ## Resolution: REJECTED marker
- Harness reads markers from files in place — no directory moves
- "Pending" = files without a ## Resolution marker
- "Addressed" = files with ## Resolution: ADDRESSED
- "Rejected" = files with ## Resolution: REJECTED

Advantages:
- Eliminates file movement logic (race conditions, partial moves, error handling)
- Single source of truth (the marker) instead of dual tracking (marker + directory)
- Simpler to debug (all feedback files in one directory, markers visible)
- Same robustness — the marker is what the harness checks anyway
- Git diff is cleaner (modified files vs. renamed files)

Files affected:
- doc/plan/granular-feedback-loop.md (simplify file lifecycle section)
- doc/schema/ai-out-directory.md (simplify __feedback/ directory structure)
- Feedback processing implementation in PartExecutor

--------------------------------------------------------------------------------
DECISION:
LETS keep the harness moving the files, lets add documentation on the WHY this is the case is so it'
s easier to track what is happening between the files

## Resolution

**Decision: Keep file movement. Added WHY documentation.**

The proposal to remove file movement was evaluated and declined. File movement provides
**observability benefits** that markers alone cannot:

1. `ls pending/` / `ls addressed/` / `ls rejected/` = instant state dashboard, zero parsing
2. `git log` shows file renames that map 1:1 to state transitions — auditable without inspecting content
3. Markers remain the canonical source of truth for harness logic; directories are deliberate redundancy for humans

**Changes made:**
- `doc/plan/granular-feedback-loop.md` — D6: added "Why move files at all" rationale paragraph
- `doc/schema/ai-out-directory.md` — `__feedback/` section: added same observability rationale
