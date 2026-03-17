---
closed_iso: 2026-03-17T21:30:48Z
id: nid_p2pbpopi88d2nj7bchj8jgpba_E
title: "SIMPLIFY_CANDIDATE: Collapse feedback sub-directories into single __feedback/ with inline resolution markers"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:23:22Z
status_updated_iso: 2026-03-17T21:30:48Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, feedback-loop, robustness, file-io]
---


FEEDBACK:

--------------------------------------------------------------------------------
## Notes

**2026-03-17T21:23:50Z**

The granular feedback loop (doc/plan/granular-feedback-loop.md) uses three sub-directories: __feedback/pending/, __feedback/addressed/, __feedback/rejected/. This requires harness-side file movement on every state transition and scanning multiple directories to determine state. Simpler approach: keep ALL feedback files in a single __feedback/ directory. State is derived from an inline ## Resolution: marker in the file content (no marker=pending, ADDRESSED or REJECTED marker=resolved). Benefits: eliminates file-movement logic in harness, state detection = single file read instead of directory scan, cleaner git history, simpler executor logic. See also: doc/core/PartExecutor.md step 4 (inner feedback loop).

--------------------------------------------------------------------------------
DECISION:
We want to keep the separate files, encoding harness to move the files is straightforward task. While it very much improves the glanceability of what is happening (for engineer checking the flow) and makes it easy to see rejected feedback. Add the justification of why we want the harness to move the files. 
**2026-03-17T21:30:54Z**

Resolution: Kept separate __feedback/pending/, addressed/, rejected/ sub-directories. Strengthened D6 in doc/plan/granular-feedback-loop.md with explicit 'Simplification candidate evaluated and rejected' section. Three numbered reasons: (1) glanceability for engineers, (2) rejected feedback immediately visible via ls rejected/, (3) harness file-movement is a low-cost standard rename. Commit: 577d5f2.
