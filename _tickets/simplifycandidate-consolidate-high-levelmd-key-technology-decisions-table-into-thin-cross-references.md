---
id: nid_vi76mhrrjybl3aksb8xvxwpbb_E
title: "SIMPLIFY_CANDIDATE: Consolidate high-level.md Key Technology Decisions table into thin cross-references"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:47:39Z
status_updated_iso: 2026-03-18T14:47:39Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, docs]
---

The Key Technology Decisions table in doc/high-level.md is a 30+ row table that duplicates design rationale already documented in individual spec files. This creates a maintenance burden and drift risk.

## Current Problem
- doc/high-level.md contains a massive table with detailed decision descriptions (often 3-5 lines per row)
- The same decisions are documented in their authoritative spec files (e.g., AgentFacade.md, agent-to-server-communication-protocol.md, git.md)
- When a decision changes in the authoritative spec, high-level.md must also be updated
- This has already led to (or will lead to) drift between the two locations
- The table makes doc/high-level.md excessively long, burying the truly unique high-level content

## Proposed Simplification
Replace the detailed table with a thin summary table:
- Each row: 1-line summary + AP reference to the authoritative spec
- Example: instead of 5-line decision description, use: "Virtual time via Clock + TestDispatcher — see ref.ap.X.E"
- Detailed rationale lives ONLY in the individual spec (single source of truth)

## Why This Improves Robustness
- Single source of truth for each decision (eliminates drift risk)
- doc/high-level.md becomes faster to read (less noise, more navigational)
- Changes to decisions only need to be made in one place
- AP references are stable identifiers that survive file renames

## What We Preserve
- All decision rationale (just deduplicated to authoritative specs)
- High-level navigational overview (the summary table still lists all decisions)
- AP cross-references for easy navigation

## Specs Affected
- doc/high-level.md (primary — replace detailed table with summary + AP refs)

