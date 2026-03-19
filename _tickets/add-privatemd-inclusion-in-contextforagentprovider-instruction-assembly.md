---
id: nid_6kqfuee0ryuf45se8c06t6v3a_E
title: "Add PRIVATE.md inclusion in ContextForAgentProvider instruction assembly"
status: in_progress
deps: [nid_9kic96nh6mb8r5legcsvt46uy_E]
links: []
created_iso: 2026-03-19T00:41:36Z
status_updated_iso: 2026-03-19T14:23:15Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, compaction]
---

## Context

Spec: `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E), R6.
ContextForAgentProvider spec: ref.ap.9HksYVzl1KkR9E1L2x8Tx.E

After self-compaction, the new session must receive the agent's prior context summary from PRIVATE.md.\n\n## What to Implement\n\nLocation: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`\n\n### 1. Add privateMdPath to AgentInstructionRequest\n\nAdd an optional `privateMdPath: Path?` field to the base `AgentInstructionRequest` sealed class (or to each subtype). When the file exists, its content is included in instruction assembly.\n\nLocation: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`\n\n```kotlin\n// Add to each request subtype:\nval privateMdPath: Path?  // null on first run; non-null after self-compaction\n```\n\n### 2. Include PRIVATE.md content in instruction assembly\n\nPosition in concatenation order — inserted between role definition and part context (position 1b):\n\n| # | Section | Notes |\n|---|---------|-------|\n| 1 | Role definition | Unchanged |\n| **1b** | **PRIVATE.md (if exists)** | NEW — from prior session self-compaction |\n| 2 | Part context | Unchanged |\n| ... | remaining sections | Unchanged |\n\n**Inclusion rule:** If `privateMdPath` is non-null AND the file exists AND is non-empty → include with header. If null or file missing → skip silently (no error — most sub-parts never self-compact).\n\n### 3. Rendering\n\nAdd a section header in InstructionText/InstructionRenderers:\n```markdown\n## Prior Session Context\n\n<content of PRIVATE.md>\n```\n\nApplies to ALL instruction assembly methods (DoerRequest, ReviewerRequest, PlannerRequest, PlanReviewerRequest).\n\n## Tests\n\n1. Unit test: instruction assembly WITH privateMdPath pointing to existing file → output contains PRIVATE.md content\n2. Unit test: instruction assembly WITH privateMdPath = null → output does NOT contain prior session section\n3. Unit test: instruction assembly WITH privateMdPath pointing to non-existent file → output does NOT contain prior session section (no error)\n4. Unit test: instruction assembly WITH privateMdPath pointing to empty file → output does NOT contain prior session section\n5. Verify position: PRIVATE.md content appears after role definition, before part context\n6. Test all request types (Doer, Reviewer, Planner, PlanReviewer)\n\n## Acceptance Criteria\n\n- `AgentInstructionRequest` subtypes have `privateMdPath: Path?` field\n- ContextForAgentProviderImpl includes PRIVATE.md when present\n- Existing tests still pass (no regressions — privateMdPath defaults to null)\n- `./test.sh` passes

