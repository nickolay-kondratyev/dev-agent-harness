# Implementation: SignalCallbackDispatcher

## What Was Done

Created `SignalCallbackDispatcher` (ap.olc7abIAv3YNk3PEE92SY.E) -- the dispatch layer between HTTP routing and the session registry. When the HTTP server receives a signal callback, it parses the JSON and delegates to this dispatcher.

### Signal Mapping
| Action | Payload Fields | AgentSignal |
|--------|---------------|-------------|
| `done` | `handshakeGuid`, `result` (completed/pass/needs_iteration) | `AgentSignal.Done(DoneResult.XXX)` |
| `fail-workflow` | `handshakeGuid`, `reason` | `AgentSignal.FailWorkflow(reason)` |
| `self-compacted` | `handshakeGuid` | `AgentSignal.SelfCompacted` |

### DispatchResult
Sealed class with three variants mapping to HTTP status codes:
- `Success(signal)` -> 200
- `SessionNotFound(guid)` -> 404
- `BadRequest(message)` -> 400

### SessionEntry.lastActivityTimestamp
Changed from `val` to `@Volatile var` to allow timestamp updates on signal callbacks. Follows the same pattern as `SpawnedAgentHandle.lastActivityTimestamp`.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcher.kt` | **NEW** - Dispatcher + DispatchResult sealed class |
| `app/src/test/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcherTest.kt` | **NEW** - 15 BDD test cases |
| `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` | `lastActivityTimestamp`: `val` -> `@Volatile var` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` | Added `HANDSHAKE_GUID` and `SIGNAL_ACTION` val types |

## Tests
All 15 test cases pass. Full suite (`./test.sh`) passes with BUILD SUCCESSFUL.

Test coverage:
- self-compacted signal dispatch -> SelfCompacted + deferred completion + timestamp update
- done signal with each result variant (completed, pass, needs_iteration)
- fail-workflow signal dispatch -> FailWorkflow + deferred completion + timestamp update
- Session not found returns SessionNotFound
- Missing handshakeGuid returns BadRequest
- Missing result field on done returns BadRequest
- Invalid done result returns BadRequest
- Missing reason field on fail-workflow returns BadRequest
- Unknown action returns BadRequest

## Design Decisions
- **Clock injection** for testability -- uses `Clock.fixed()` in tests, `Clock.systemUTC()` in production
- **Map-based payload** (`Map<String, String>`) rather than a typed DTO -- keeps the dispatcher decoupled from any specific JSON library. The HTTP server layer handles JSON parsing.
- **ProtocolVocabulary constants** used for all action and result matching (compile-time link)
- **DONE_RESULT_MAP** lookup instead of `when` to satisfy detekt's ReturnCount rule
