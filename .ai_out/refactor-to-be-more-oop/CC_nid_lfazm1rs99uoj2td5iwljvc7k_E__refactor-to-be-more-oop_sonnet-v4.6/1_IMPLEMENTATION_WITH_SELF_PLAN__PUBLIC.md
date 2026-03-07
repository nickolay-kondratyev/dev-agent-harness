# Implementation: Refactor to be more OOP

## Status: COMPLETE

## Build Result
BUILD SUCCESSFUL — `./gradlew :app:build` passes with no errors.

## Summary of Changes

### 1. `TmuxCommunicator.kt` — Added interface, renamed class
- Added `interface TmuxCommunicator` with `sendKeys` and `sendRawKeys` methods
- Renamed existing class to `TmuxCommunicatorImpl` implementing the interface
- `@AnchorPoint("ap.3BCYPiR792a2B8I9ZONDwmvN.E")` preserved on `TmuxCommunicatorImpl`
- Both interface and implementation live in the same file

### 2. `TmuxSession.kt` — NEW file
- New class `TmuxSession` holding:
  - `val name: TmuxSessionName` (public)
  - `private val communicator: TmuxCommunicator`
  - `private val existsChecker: suspend () -> Boolean` (lambda to avoid circular dep with `TmuxSessionManager`)
- Exposes `sendKeys(text)`, `sendRawKeys(keys)`, and `exists()` — session is now the primary interaction point

### 3. `TmuxSessionManager.kt` — Updated signatures
- Added `private val communicator: TmuxCommunicator` constructor param
- `createSession()` now returns `TmuxSession` (was `TmuxSessionName`)
- `killSession()` now takes `TmuxSession` (was `TmuxSessionName`)
- `sessionExists()` is now `private` — internal logic exposed via `TmuxSession.exists()`

### 4. `App.kt` — Updated wiring
- Imports `TmuxCommunicatorImpl` instead of `TmuxCommunicator` (the old class name)
- Wires `communicator` to `TmuxSessionManager` constructor
- Uses `session.sendKeys(...)` directly instead of `communicator.sendKeys(session, ...)`

### 5. `TmuxCommunicatorTest.kt` — Updated to new API
- `createdSessions` type: `TmuxSessionName` → `TmuxSession`
- Added `communicator` to `TmuxSessionManager` constructor call
- `communicator.sendKeys(session, ...)` → `session.sendKeys(...)`
- Imports updated (`TmuxCommunicatorImpl`, `TmuxSession`)

### 6. `TmuxSessionManagerTest.kt` — Updated to new API
- `createdSessions` type: `TmuxSessionName` → `TmuxSession`
- Added `communicator` to `TmuxSessionManager` constructor call
- All `sessionManager.sessionExists(sessionName)` calls → `session.exists()`
- "WHEN sessionExists with non-existent name" test replaced with "WHEN killSession is called / THEN exists() returns false" (same behavior, better OOP expression)
- Imports updated (`TmuxCommunicatorImpl`, `TmuxSession`)

## Design Decisions
- Lambda `existsChecker: suspend () -> Boolean` in `TmuxSession` avoids circular dependency between `TmuxSession` and `TmuxSessionManager`
- Interface and implementation in same file (per CLAUDE.md DIP standards)
- `sessionExists` private on `TmuxSessionManager` — clean encapsulation, callers use `session.exists()`

## Files Modified
- `app/src/main/kotlin/com/glassthought/tmux/TmuxCommunicator.kt`
- `app/src/main/kotlin/com/glassthought/tmux/TmuxSession.kt` (NEW)
- `app/src/main/kotlin/com/glassthought/tmux/TmuxSessionManager.kt`
- `app/src/main/kotlin/org/example/App.kt`
- `app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt`
- `app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt`
