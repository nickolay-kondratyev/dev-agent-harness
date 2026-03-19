---
id: nid_156yspl7869zrisam6l6xrd66_E
title: "Implement TryNResolver — resolve try number from .ai_out/ directories"
status: in_progress
deps: [nid_9kic96nh6mb8r5legcsvt46uy_E]
links: []
created_iso: 2026-03-18T23:44:11Z
status_updated_iso: 2026-03-19T17:41:45Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [git, startup]
---

Implement TryNResolver that determines the next try-N number by scanning .ai_out/ directories.

## Spec Reference
- doc/core/git.md lines 79-92 (Try-N Resolution section)

## Algorithm
1. Build candidate branch name via `BranchNameBuilder.build(ticket, candidateN)`
2. Check: does `.ai_out/{candidate}/` directory exist?
3. If exists → increment candidateN, repeat
4. If not → use candidateN
- Start from candidateN = 1

## Rationale
- `.ai_out/` directories are always created as part of initialization (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
- No git operations needed — reduces failure surface
- Single source of truth: the filesystem

## Interface Design
- Create `TryNResolver` interface + `TryNResolverImpl` in `com.glassthought.shepherd.core.supporting.git`
- Method: `suspend fun resolve(ticketData: TicketData): Int` — returns the next try number
- Depends on: `BranchNameBuilder` (already exists at app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt)
- Depends on: `AiOutputStructure` for knowing the .ai_out/ root path (ticket nid_9kic96nh6mb8r5legcsvt46uy_E)

## Owner
- Called by TicketShepherdCreator (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) during branch creation

## Testing
- Unit tests: no .ai_out/ dirs → returns 1; .ai_out/ for try-1 exists → returns 2; gap detection (try-1 and try-3 exist → returns 2)
- Use temp directories for filesystem tests

## Existing Code Context
- BranchNameBuilder at app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt
- AiOutputStructure ticket: nid_9kic96nh6mb8r5legcsvt46uy_E

