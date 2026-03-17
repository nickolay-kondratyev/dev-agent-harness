---
id: nid_p2pbpopi88d2nj7bchj8jgpba_E
title: "SIMPLIFY_CANDIDATE: Collapse feedback sub-directories into single __feedback/ with inline resolution markers"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:23:22Z
status_updated_iso: 2026-03-17T21:23:22Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, feedback-loop, robustness, file-io]
---


## Notes

**2026-03-17T21:23:50Z**

The granular feedback loop (doc/plan/granular-feedback-loop.md) uses three sub-directories: __feedback/pending/, __feedback/addressed/, __feedback/rejected/. This requires harness-side file movement on every state transition and scanning multiple directories to determine state. Simpler approach: keep ALL feedback files in a single __feedback/ directory. State is derived from an inline ## Resolution: marker in the file content (no marker=pending, ADDRESSED or REJECTED marker=resolved). Benefits: eliminates file-movement logic in harness, state detection = single file read instead of directory scan, cleaner git history, simpler executor logic. See also: doc/core/PartExecutor.md step 4 (inner feedback loop).
