---
id: nid_yzmwosyazxksnr1hafmw87x1m_E
title: "Implement Part Completion Guard — validate no pending critical/important on reviewer PASS"
status: in_progress
deps: [nid_fjod8du6esers3ajur2h7tvgx_E, nid_92vpmdxcn3j8f98gzgu9eln43_E, nid_5z93biuqub3mhcejfpofjmj39_E]
links: []
created_iso: 2026-03-18T22:53:10Z
status_updated_iso: 2026-03-19T19:36:58Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [granular-feedback-loop, part-executor, guard]
---

Gate 5 from granular feedback loop spec (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

## Context
Split from nid_fq8wn0eb9yrvzcpzdurlmsg7i_E to keep that ticket focused on the inner loop orchestration.

## Requirement (R8)
On reviewer PASS, before allowing PartResult.Completed:
1. PUBLIC.md validation (existing)
2. Validate __feedback/pending/ contains no critical__* files
3. Validate __feedback/pending/ contains no important__* files
4. If 2 or 3 fails → PartResult.AgentCrashed("Reviewer signaled pass with unaddressed critical/important feedback items in pending/")
5. Remaining optional__* in pending/ → move to addressed/ (implicitly accepted as skipped)

## Location
PartExecutorImpl — step 3 (On reviewer PASS) extension.
Package: com.glassthought.shepherd.core

## Testing (via FakeAgentFacade + virtual time)
- Unit test: PASS with empty pending → Completed
- Unit test: PASS with pending critical__* → immediate AgentCrashed
- Unit test: PASS with pending important__* → immediate AgentCrashed
- Unit test: PASS with only optional__* in pending → Completed (optional moved to addressed/)
- Unit test: PASS with mix of optional + no critical/important → Completed

## Dependencies
- AiOutputStructure path resolution for __feedback/ dirs
- FeedbackResolutionParser (to validate optional files if needed)

## Acceptance Criteria
- All unit tests pass
- Part completion correctly guarded — no silent acceptance of unaddressed critical/important items


## Notes

**2026-03-19T00:51:27Z**

Added missing dep: PartExecutorImpl (nid_5z93biuqub3mhcejfpofjmj39_E) — this ticket extends step 3 (PASS branch) of PartExecutorImpl.
