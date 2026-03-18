---
id: nid_6o206wjjli6d6xzlw6s38mju2_E
title: "SIMPLIFY_CANDIDATE: Move V2 design content from V1 specs to doc_v2/ directory"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T14:47:21Z
status_updated_iso: 2026-03-18T14:59:23Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, docs]
---

V1 specs contain extensive V2 design notes interleaved with V1 behavior, making V1 behavior harder to extract and implement correctly.

## Current Problem
Multiple V1 specs include multi-paragraph V2 design sections:
- doc/use-case/ContextWindowSelfCompactionUseCase.md: includes multi-page V2 emergency-compression design
- doc/high-level.md: includes V2 design decisions table entries (resume, parallel agents, idle session recovery)
- doc/use-case/SpawnTmuxAgentSessionUseCase.md: includes V2 resume flow
- doc/core/PartExecutor.md: references V2 emergency compaction flow

This interleaving makes V1 specs 20-30% longer than necessary and creates ambiguity about what is V1 vs V2 behavior.

## Proposed Simplification
1. Create doc_v2/ directory for all V2 design content
2. Extract V2 sections from V1 specs into doc_v2/ files (preserving AP references)
3. In V1 specs, replace extracted content with single-sentence forward references: "V2 will add emergency interrupt (see ref.ap.X.E in doc_v2/)"
4. PRESERVE "do not revisit" and "rejected approach" sections in V1 — these block alternative approaches and must stay

## Why This Improves Robustness
- V1 specs become the unambiguous source of truth for V1 behavior
- Implementors cannot accidentally implement V2 behavior thinking it is V1
- Shorter specs are faster to read and less error-prone to implement
- V2 design content is cohesive in one location, easier to plan V2 from

## What We Preserve
- All V2 design content (just relocated, not deleted)
- "Do not revisit" decisions (remain in V1 specs)
- AP cross-references (updated to point to new locations)

## Specs Affected
- doc/use-case/ContextWindowSelfCompactionUseCase.md
- doc/high-level.md
- doc/use-case/SpawnTmuxAgentSessionUseCase.md
- doc/core/PartExecutor.md
- Possibly others with V2 references

