---
id: nid_o4gj7swdejriooj5bex3b34vf_E
title: "Align InstructionText + ProtocolVocabulary + InstructionRenderers with new 3-directory feedback structure"
status: open
deps: []
links: []
created_iso: 2026-03-18T18:46:42Z
status_updated_iso: 2026-03-18T18:46:42Z
type: chore
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ContextForAgentProvider, feedback-loop, consistency]
---

Current code references the OLD 9-directory feedback structure (3 statuses × 3 severities).
The spec (doc/plan/granular-feedback-loop.md ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) changed to 3 directories
(pending/, addressed/, rejected/) with severity encoded in filename prefixes (critical__, important__, optional__).

## What Needs Updating

### 1. InstructionText.FEEDBACK_WRITING_INSTRUCTIONS
File: app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionText.kt
- Currently references __feedback/unaddressed/critical/, __feedback/unaddressed/important/, etc.
- Must reference __feedback/pending/ with severity filename prefixes
- Must describe: one .md file per issue, named {severity}__{descriptive-slug}.md
- Must list valid prefixes: critical__, important__, optional__

### 2. ProtocolVocabulary.FeedbackStatus
File: app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt
- FeedbackStatus.UNADDRESSED → align with pending/ directory naming
- Remove MOVEMENT_LOG references if present (agents no longer move files)
- Ensure resolution markers match: ADDRESSED, REJECTED, SKIPPED

### 3. InstructionRenderers.feedbackItemInstructions()
File: app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionRenderers.kt
- Currently uses old movement-log-based pattern (agents moved files, wrote movement records)
- New spec: agents write ## Resolution: markers; harness moves files
- Update resolution instructions: ADDRESSED/REJECTED/SKIPPED

### 4. Test fixtures
File: app/src/test/resources/com/glassthought/shepherd/core/context/fixtures/feedback/
- Currently uses old layout: unaddressed/critical/, addressed/critical/, rejected/important/
- Must use new layout: pending/, addressed/, rejected/ with severity-prefixed filenames

## Why This Matters
The feedback-loop InstructionSection subtypes (nid_gp9rduvxoqf14m95z9bttnaxq_E) will
build on these static text and vocabulary constants. Updating them first ensures the new
sections use correct content from day one.

## Spec References
- doc/plan/granular-feedback-loop.md (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) — D1, D6, __feedback/ directory section
- doc/core/ContextForAgentProvider.md (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) — Doer row 7a, Reviewer rows 6a-6e


## Notes

**2026-03-18T18:50:06Z**

## Clarifications from review

### ProtocolVocabulary actions (explicit):
- **DELETE** `FeedbackStatus.UNADDRESSED` — replaced by directory name `pending/` directly
- **DELETE** `MOVEMENT_LOG` constant — agents no longer write movement records
- **KEEP** `FeedbackStatus.ADDRESSED`, `FeedbackStatus.REJECTED` — these match new resolution markers
- **ADD** `FeedbackStatus.SKIPPED` — new resolution marker for optional items
- **ADD** `Severity.CRITICAL_PREFIX = "critical__"`, `Severity.IMPORTANT_PREFIX = "important__"`, `Severity.OPTIONAL_PREFIX = "optional__"` — new filename prefix constants

### Test fixture paths (explicit):
- `app/src/test/resources/com/glassthought/shepherd/core/context/fixtures/feedback/unaddressed/critical/missing-null-check.md` → move to `pending/critical__missing-null-check.md`
- `app/src/test/resources/com/glassthought/shepherd/core/context/fixtures/feedback/addressed/critical/` → move to `addressed/`
- `app/src/test/resources/com/glassthought/shepherd/core/context/fixtures/feedback/rejected/important/` → move to `rejected/`
- Flatten all 9-directory fixtures into 3-directory structure with severity filename prefixes
