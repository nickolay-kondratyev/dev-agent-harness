---
closed_iso: 2026-03-18T15:10:46Z
id: nid_6o206wjjli6d6xzlw6s38mju2_E
title: "SIMPLIFY_CANDIDATE: Move V2 design content from V1 specs to doc_v2/ directory"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:47:21Z
status_updated_iso: 2026-03-18T15:10:46Z
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
- doc/core/UserQuestionHandler.md

## Resolution

Completed. V2 content extracted from V1 specs into doc_v2/ directory:

### What was extracted:
1. **SpawnTmuxAgentSessionUseCase.md**: Resume Flow section (~28 lines) + resume command template + `--resume` line from ClaudeCodeAdapter → merged into `doc_v2/resume.md`
2. **ContextWindowSelfCompactionUseCase.md**: V2 Resume section + idle session recovery V2 comparison (~15 lines) → condensed to forward refs pointing to `doc_v2/resume.md` and `doc_v2/idle-session-recovery.md`
3. **high-level.md**: "Harness-Level Resume — V2" section condensed from 5 lines to 2-line forward ref
4. **UserQuestionHandler.md**: "Future Strategies (V2+)" strategy table → new `doc_v2/user-question-handler-future-strategies.md`
5. **PartExecutor.md**: Fixed bare `doc_v2/` reference to proper markdown link

### What was preserved:
- All "do not revisit" and "rejected approach" sections remained in V1 specs
- All AP cross-references updated and verified
- Brief one-liner V2 forward refs across other specs left as-is (appropriate context)
- Fixed stale "see V2+ table below" reference in UserQuestionHandler.md

### Analysis findings:
- 12 additional files with V2/doc_v2 references were analyzed — all contained only brief forward references, no substantial V2 design content to extract
- Some V2 emergency-compression content had already been extracted to `doc_v2/our-own-emergency-compression.md` prior to this ticket

