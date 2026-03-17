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

Current design: A SubPartRole enum (DOER, REVIEWER) is tracked separately in SessionEntry, request objects (DoerInstructionRequest, ReviewerInstructionRequest), and PartExecutorImpl validation logic.

Problem: The role is always deterministic from sub-part position — position 0 = DOER, position 1 = REVIEWER. This is a hard constraint in the spec ("at most 2 sub-parts per part; first sub-part is doer, optional second sub-part is reviewer" — doc/high-level.md). There is no configuration path that makes position-0 a reviewer or position-1 a doer. The enum adds a tracking obligation without adding expressiveness.

Spec reference: doc/high-level.md Hard Constraints, doc/core/PartExecutor.md (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E)

Simpler approach: Remove the SubPartRole enum. Use sub-part index (Int) in SessionEntry. Compute role on demand: val role = if (subPartIndex == 0) "doer" else "reviewer". The ContextForAgentProvider methods already differentiate by request type (DoerInstructionRequest vs ReviewerInstructionRequest) — that type distinction IS the role information, no enum needed.

Benefits:
- Eliminates a source of potential mismatch (role field set to wrong value for position)
- The position-to-role relationship becomes code-explicit in one tiny helper
- One less thing to track in SessionEntry
- V2 extension: if multiple doers are ever supported, the position-based model extends naturally

Impact: Remove SubPartRole enum. Update SessionEntry to use subPartIndex: Int. Update server-side validation (role-based) to derive role from index. Update GitCommitStrategy.onSubPartDone hook context.

