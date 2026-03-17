---
id: nid_d3yo168rp46gu1odszeve8gun_E
title: "SIMPLIFY_CANDIDATE: YAGNI — remove agentSessionId from SessionRecord schema, use only agentSessionPath for V1 ClaudeCode"
status: open
deps: []
links: []
created_iso: 2026-03-17T22:47:29Z
status_updated_iso: 2026-03-17T22:47:29Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, yagni, schema, v1]
---

## Problem
The `SessionRecord` schema (doc/schema/plan-and-current-state.md) defines an OR-branch:

```json
{
  "handshakeGuid": "...",
  "agentSessionId": "...",   // OR
  "agentSessionPath": "...", // depending on agent type
  "agentType": "ClaudeCode",
  "model": "...",
  "timestamp": "..."
}
```

V1 only supports `ClaudeCode`, which uses `agentSessionPath`. The `agentSessionId` field is YAGNI — it exists for a future PI agent that is NOT in V1 scope.

This OR-branch requires every reader/writer of `SessionRecord` to handle both cases conditionally: "does this record have `agentSessionId` or `agentSessionPath`?"

## Proposed Simplification
- V1: Use only `agentSessionPath` in `SessionRecord`. Remove `agentSessionId` from schema and implementation.
- When PI agent support is added in V2+, add `agentSessionId` then (trivial extension — add a field).

## Why This Improves Both
- **Simpler**: One code path everywhere SessionRecord is read/written; no conditional branches
- **More robust**: Cannot accidentally write a record with the wrong field; no null-handling for the unused field
- **YAGNI principle**: Do not build for PI agent support until it is scheduled

## Note
This is a V1-scoped simplification. V2 can reintroduce the OR-branch when PI agent support is actually planned. The schema doc should clearly annotate the removed field as V2+.

## Acceptance Criteria

- SessionRecord schema has only `agentSessionPath` (no `agentSessionId`)
- Schema doc updated with V2+ note on agentSessionId
- All SessionRecord readers/writers simplified (no conditional branch)
- Existing tests pass

