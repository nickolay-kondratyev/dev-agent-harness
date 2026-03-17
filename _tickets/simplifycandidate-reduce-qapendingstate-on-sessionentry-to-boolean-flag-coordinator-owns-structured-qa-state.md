---
id: nid_n8608ym27ipcqke4iwd0934g7_E
title: "SIMPLIFY_CANDIDATE: Reduce QAPendingState on SessionEntry to boolean flag — coordinator owns structured Q&A state"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T23:40:32Z
status_updated_iso: 2026-03-17T23:49:42Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec, sessions]
---

## Problem

SessionEntry (ref.ap.igClEuLMC0bn7mDrK41jQ.E) carries a `pendingQA: QAPendingState?` field containing the full question queue and collected answers (`questions: List<PendingQuestion>`, `answers: List<String>`). However, the executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) only reads the derived `isQAPending` boolean to gate health pings, compaction, and noActivityTimeout. The full QAPendingState is consumed only by the Q&A coordinator (ref.ap.NE4puAzULta4xlOLh5kfD.E).

This means SessionEntry holds detailed Q&A state that only one consumer needs, while all other consumers (the health loop) need a simple boolean.

## Simplification

Replace `pendingQA: QAPendingState?` on SessionEntry with a simple `isQAPending: Boolean` flag (or `AtomicBoolean` for thread safety). The Q&A coordinator owns its own internal `QAPendingState` — the structured question/answer queue becomes an implementation detail of the coordinator, not a shared field on SessionEntry.

### What changes
- SessionEntry: `pendingQA: QAPendingState?` → `isQAPending: Boolean` (set by coordinator)
- Q&A coordinator: owns internal `QAPendingState` (questions, answers)
- Server `/signal/user-question` handler: sets `isQAPending = true` + notifies coordinator (instead of mutating QAPendingState on SessionEntry)
- Coordinator clears `isQAPending = false` after answer delivery

## Why This Improves Robustness

- Clearer ownership boundary — coordinator owns Q&A state, SessionEntry owns the signal to the health loop
- Simpler SessionEntry — fewer fields, easier to reason about concurrency
- Reduces surface area for concurrency bugs — the server and coordinator no longer both mutate the same `QAPendingState` object on SessionEntry
- No behavioral change for the executor or health loop

## Affected Specs

- `doc/core/SessionsState.md` — SessionEntry fields, QAPendingState section
- `doc/core/UserQuestionHandler.md` — Q&A coordinator lifecycle, flow
- `doc/core/agent-to-server-communication-protocol.md` — user-question routing

