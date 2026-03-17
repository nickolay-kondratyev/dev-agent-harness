---
closed_iso: 2026-03-17T23:13:08Z
id: nid_d3yo168rp46gu1odszeve8gun_E
title: "SIMPLIFY_CANDIDATE: YAGNI — remove agentSessionId from SessionRecord schema, use only agentSessionPath for V1 ClaudeCode"
status: closed
deps: []
links: []
created_iso: 2026-03-17T22:47:29Z
status_updated_iso: 2026-03-17T23:13:08Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, yagni, schema, v1]
---


FEEDBACK:
--------------------------------------------------------------------------------
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

--------------------------------------------------------------------------------

DECISION: we actually only use `agentSessionId` for Claude Code lets re-align the documentation to state that `agentSessionId` is used for Claude Code. Now what i am thinking to make it clear is to have a sub-object:
```json
{
  "handshakeGuid": "...",
  "agentSession": {
    "id":""
  },
  "agentType": "ClaudeCode",
  "model": "...",
  "timestamp": "..."
}
```
And we can start with only having `agentSession.id` to start out with.

## Resolution

Replaced the `agentSessionId`/`agentSessionPath` OR-branch with a unified `agentSession: { id }` sub-object in the SessionRecord schema. Changes:

1. **`doc/schema/plan-and-current-state.md`** — Updated Session Record Schema table: removed `agentSessionId` (optional) and `agentSessionPath` (optional) fields, replaced with `agentSession` (required) sub-object containing `agentSession.id` (required). Updated all 5 JSON examples. Added V2+ HTML comment noting that `agentSession` can be extended with additional fields (e.g., `path`) when other agent types are supported.
2. **`doc/use-case/SpawnTmuxAgentSessionUseCase.md`** — Updated cross-reference to session record fields.
3. **`doc_v2/idle-session-recovery.md`** — Updated `--resume` reference from `agentSessionId` to `agentSession.id`.

**Not changed** (correctly so): Kotlin interface parameter names (`agentSessionId` in `ContextWindowStateReader.read()` etc.) — these are internal code names, not JSON schema fields. They take the value that now comes from `agentSession.id` in the schema.
