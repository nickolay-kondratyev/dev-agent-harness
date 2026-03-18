---
closed_iso: 2026-03-18T14:41:48Z
id: nid_85ipssz197fvyewlbral2mli8_E
title: "SIMPLIFY_CANDIDATE: Remove single-value CompactionTrigger enum — just use a direct method"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:24:32Z
status_updated_iso: 2026-03-18T14:41:48Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, yagni]
---

## Problem

`CompactionTrigger` enum has exactly one value: `DONE_BOUNDARY`. It exists for V2 extensibility (`EMERGENCY_INTERRUPT`). In V1, the enum, the `performCompaction(handle, trigger)` method signature, and any `when` branches are unnecessary ceremony.

## Spec Reference

- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E)

## Proposed Change

Remove the `CompactionTrigger` enum entirely. Replace `performCompaction(handle, trigger)` with `performDoneBoundaryCompaction(handle)`. When V2 adds emergency compaction, introduce the enum then.

## Justification

- **YAGNI**: V2 emergency compaction is speculative; single-value enums are dead abstraction.
- **Simpler**: Removes one type, simplifies method signatures, eliminates `when` branches.
- **Robustness unchanged**: No behavioral change — just removes unused indirection.
- **Easy to reverse**: If V2 needs the enum, adding it back is trivial.

