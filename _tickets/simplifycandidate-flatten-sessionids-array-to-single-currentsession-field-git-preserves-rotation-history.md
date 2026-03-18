---
closed_iso: 2026-03-18T13:44:32Z
id: nid_ndift4w7dvm2hsozycuxlku4g_E
title: "SIMPLIFY_CANDIDATE: Flatten sessionIds array to single currentSession field — git preserves rotation history"
status: closed
deps: []
links: []
created_iso: 2026-03-18T00:07:17Z
status_updated_iso: 2026-03-18T13:44:32Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, spec-change]
---

## Current Design (ref.ap.mwzGc1hYkVwu3IJQbTeW4.E)

`current_state.json` stores `sessionIds` as an **array** on each sub-part. The last element is the current/resumable session. New entries are appended on:
- Initial spawn
- Session rotation (context window compaction creates new session)

## Problem

The array convention adds unnecessary complexity:
- "Last element = current" is a convention that must be documented and enforced
- Array append logic on every session rotation
- Indexing into array to find current session
- Array grows unboundedly within a single run (every compaction adds an entry)

The history this array provides (which sessions existed for this sub-part) is already preserved in **git history** — every `current_state.json` write is followed by a git commit. The full timeline of session records is recoverable from `git log`.

## Proposed Simplification

Replace `sessionIds: [...]` with `currentSession: {...}` — a single session record object (or null/absent before first spawn).

On session rotation (compaction): **replace** `currentSession` with new session record. Old session record is in the previous git commit.

```json
// Before
"sessionIds": [
  { "handshakeGuid": "handshake.old...", "agentSession": { "id": "..." }, ... },
  { "handshakeGuid": "handshake.new...", "agentSession": { "id": "..." }, ... }
]

// After
"currentSession": {
  "handshakeGuid": "handshake.new...",
  "agentSession": { "id": "..." },
  "agentType": "ClaudeCode",
  "model": "sonnet",
  "timestamp": "2026-03-10T15:30:00Z"
}
```

## Why This Is Both Simpler AND More Robust

- No "last element" convention to learn/enforce
- No array indexing — direct field access
- No unbounded growth within a single run
- Simpler serialization/deserialization (object vs array-of-objects)
- Git history is the authoritative audit trail anyway
- V2 resume only needs the current session — direct field access is clearer than array.last()

## Spec Files to Update

- `doc/schema/plan-and-current-state.md` (session record schema, examples)
- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (session rotation)
- `doc/use-case/SpawnTmuxAgentSessionUseCase.md` (session record creation)
- `doc/high-level.md` (session storage summary)

