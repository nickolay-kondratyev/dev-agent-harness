---
closed_iso: 2026-03-19T18:53:58Z
id: nid_v14amda2uv5nedrp9hvb8xlfq_E
title: "Implement SessionsState — GUID-keyed in-memory session registry with register/lookup/removeAllForPart"
status: closed
deps: [nid_89bw63qr6qyewthjq4wp3x0so_E, nid_5o5wyxuzoz7qrkuq4wuo2gnjr_E]
links: [nid_erd0khe8sg0vqbnwtg23aqzw9_E]
created_iso: 2026-03-19T00:40:11Z
status_updated_iso: 2026-03-19T18:53:58Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, sessions-state]
---

## Context

Spec: `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E), section "Operations".

`SessionsState` is the in-memory registry of live agent sessions, keyed by `HandshakeGuid`. It bridges the HTTP server (which receives callbacks identified by GUID) with the `CompletableDeferred<AgentSignal>` that the executor is suspended on. Internal to `AgentFacadeImpl`.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionsState.kt`

```kotlin
class SessionsState(
    private val map: MutableSynchronizedMap<HandshakeGuid, SessionEntry> = MutableSynchronizedMap()
) {
    /**
     * Adds or updates a session in the registry.
     * Called by AgentFacadeImpl on initial spawn (spawnAgent) AND at the start of
     * every sendPayloadAndAwaitSignal call (fresh deferred, same GUID).
     */
    suspend fun register(guid: HandshakeGuid, entry: SessionEntry)

    /**
     * Returns SessionEntry or null. Read-only lookup (except signalDeferred.complete()
     * and lastActivityTimestamp update done by caller).
     * Called by ShepherdServer on every callback.
     */
    suspend fun lookup(guid: HandshakeGuid): SessionEntry?

    /**
     * Removes all sessions belonging to a part.
     * Called by TicketShepherd when part completes.
     * For the planning part, partName = "planning".
     */
    suspend fun removeAllForPart(partName: String): List<SessionEntry>
}
```

## Key Design Points

- Backed by `MutableSynchronizedMap` (suspend-friendly Mutex) — ticket nid_89bw63qr6qyewthjq4wp3x0so_E
- `register` does upsert — same GUID can be re-registered with fresh signalDeferred
- `lookup` returns null if GUID not found (server handles gracefully)
- `removeAllForPart` filters by `SessionEntry.partName` and removes all matching
- V1 serial execution makes per-operation synchronization sufficient
- Created by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — empty on creation
- Shared between `AgentFacadeImpl` (register/remove) and `ShepherdServer` (lookup)

## Ownership (from spec)

- Created by: `TicketShepherdCreator`
- Owned by: `AgentFacadeImpl` — registers entries during spawn and iteration, removes on part completion
- Shared with: `ShepherdServer` — holds reference for `lookup` on incoming callbacks
- NOT accessed by: `PartExecutor` — all agent operations flow through AgentFacade
- Lifecycle: lives for duration of one ticket processing. Not persisted.

## Dependencies
- `MutableSynchronizedMap` — ticket nid_89bw63qr6qyewthjq4wp3x0so_E
- `SessionEntry` — ticket nid_5o5wyxuzoz7qrkuq4wuo2gnjr_E
- `HandshakeGuid` — already exists in code at `core/agent/sessionresolver/HandshakeGuid.kt`

## Tests (BDD/DescribeSpec)

- GIVEN empty state WHEN lookup(guid) THEN returns null
- GIVEN registered session WHEN lookup(guid) THEN returns SessionEntry
- GIVEN registered session WHEN register(same guid, new entry) THEN overwrites previous
- GIVEN sessions for part A and part B WHEN removeAllForPart("A") THEN removes only A sessions
- GIVEN sessions for part A WHEN removeAllForPart("A") THEN returns removed entries
- GIVEN no sessions for part WHEN removeAllForPart("X") THEN returns empty list
- GIVEN multiple sessions for same part WHEN removeAllForPart THEN removes all of them

## Package
`com.glassthought.shepherd.core.session`

## Acceptance Criteria
- SessionsState class with register, lookup, removeAllForPart operations
- Backed by MutableSynchronizedMap for coroutine safety
- Unit tests verify all operations including edge cases
- `./test.sh` passes

