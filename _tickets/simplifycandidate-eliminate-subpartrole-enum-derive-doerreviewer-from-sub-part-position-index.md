---
id: nid_njvc0721iruumnk99k2wqmqbx_E
title: "SIMPLIFY_CANDIDATE: Eliminate SubPartRole enum — derive doer/reviewer from sub-part position index"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:32:05Z
status_updated_iso: 2026-03-17T21:32:05Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, architecture, part-executor, robustness]
---

Feedback
--------------------------------------------------------------------------------
Current design: A SubPartRole enum (DOER, REVIEWER) is tracked separately in SessionEntry, request objects (DoerInstructionRequest, ReviewerInstructionRequest), and PartExecutorImpl validation logic.

Problem: The role is always deterministic from sub-part position — position 0 = DOER, position 1 = REVIEWER. This is a hard constraint in the spec ("at most 2 sub-parts per part; first sub-part is doer, optional second sub-part is reviewer" — doc/high-level.md). There is no configuration path that makes position-0 a reviewer or position-1 a doer. The enum adds a tracking obligation without adding expressiveness.
--------------------------------------------------------------------------------

DECISION:
We want to keep the enumeration as we could have reviewer/doer combinations potentially in the future. We would rather be explicit. Update spec to capture this so that we have clear justification and keep evolution in mind, we arent building for reviewer/fixer right now but we want to be EXPLICIT to make the door for evolution more propped open.