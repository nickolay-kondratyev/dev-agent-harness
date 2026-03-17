---
id: nid_k78p6ln09ffxywfaq5r0dqimh_E
title: "SIMPLIFY_CANDIDATE: Eliminate SubPartRole enum — derive role deterministically from sub-part array position"
status: open
deps: []
links: []
created_iso: 2026-03-17T22:15:00Z
status_updated_iso: 2026-03-17T22:15:00Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, state, session]
---

--------------------------------------------------------------------------------
The spec (doc/core/SessionsState.md, doc/schema/plan-and-current-state.md) stores SubPartRole as an explicit enum field in every SessionEntry (DOER at position 0, REVIEWER at position 1).

Problem:
- SubPartRole is redundant with sub-part array position — the spec itself says position 0 = DOER, position 1 = REVIEWER in V1.
- Two sources of truth that must stay in sync: stored role enum + actual array position.
- Risk of divergence if sub-parts are reordered without updating stored role.
- Session record schema carries one extra field that must be serialized/deserialized during self-compaction (ref.ap.8nwz2AHf503xwq8fKuLcl.E).

Proposed simplification:
- Remove SubPartRole from SessionEntry and session record schema.
- Derive role on-the-fly: `fun SubPartRole.Companion.fromIndex(index: Int): SubPartRole`
- SessionEntry stores only `subPartIndex: Int`.
- Role derivation is a pure function — single source of truth, no sync needed.

Robustness improvement:
- Eliminates an entire class of divergence bugs (stored role contradicting actual position).
- Simpler schema = fewer fields to serialize during context-window session rotation.
- Future role additions: update only `fromIndex()`, not session records.

Relevant specs: doc/core/SessionsState.md, doc/schema/plan-and-current-state.md
--------------------------------------------------------------------------------

OK let's simplify. Array position would be doer and reviwer. Perform the simplification.