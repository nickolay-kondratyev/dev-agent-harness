---
id: nid_vhurz5s8p23lbyhrzfdq3gp1x_E
title: "SIMPLIFY_CANDIDATE: Simplify try-N resolution — use .ai_out/ directories as single source of truth"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:24:11Z
status_updated_iso: 2026-03-15T01:24:11Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, git, startup]
---

## Problem

Try-N resolution (ref.ap.THL21SyZzJhzInG2m4zl2.E) uses a dual-check strategy:
1. Scan **local git branches** for `{TICKET_ID}__*__try-{N}` patterns.
2. Scan **`.ai_out/` directories** for matching branch-named directories.
3. First N where NEITHER exists is chosen.

This dual-scan adds complexity:
- Two different scanning mechanisms to maintain.
- Edge cases where branches exist but directories don't (or vice versa) need handling.
- Branch scanning requires git operations that could fail.

## Proposed Simplification

Use `.ai_out/` directories as the **single source of truth** for try-N resolution.

**Rationale**: `.ai_out/` directories are always created as part of initialization (ref.ap.BXQlLDTec7cVVOrzXWfR7.E). If a try ran far enough to matter, it created its `.ai_out/` directory. If it didn't even get that far, there's nothing to conflict with.

## Benefits
- **One scan** instead of two.
- **No git operations** needed for try-N resolution — reduces failure surface.
- **Simpler logic** — single directory listing vs. cross-referencing two sources.
- **Equally robust** — .ai_out/ directory creation happens at the very start of a try, so it captures all meaningful attempts.

## Spec files affected
- `doc/core/git.md`
- `doc/core/TicketShepherdCreator.md`

