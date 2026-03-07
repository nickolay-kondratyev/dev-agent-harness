# IMPLEMENTOR PRIVATE: Refactor to be more OOP

## Task Understanding
Refactor the tmux layer to be more OOP:
1. Extract `TmuxCommunicator` interface, rename class to `TmuxCommunicatorImpl`
2. Add `TmuxSession` class wrapping `TmuxSessionName` + `TmuxCommunicator` + exists lambda
3. Update `TmuxSessionManager` — returns `TmuxSession`, takes `TmuxSession` in killSession, make sessionExists private
4. Update `App.kt` wiring
5. Update tests

## Plan

**Goal**: Refactor tmux layer for OOP encapsulation with `TmuxSession` as the primary session handle.

**Steps**:
1. [x] Read all relevant files (done)
2. [x] Modify `TmuxCommunicator.kt` — add interface + rename class to `TmuxCommunicatorImpl`
3. [x] Create `TmuxSession.kt` — new OOP session class
4. [x] Modify `TmuxSessionManager.kt` — update signatures
5. [x] Update `App.kt` — new wiring
6. [x] Update `TmuxCommunicatorTest.kt`
7. [x] Update `TmuxSessionManagerTest.kt`
8. [x] Run build to verify — BUILD SUCCESSFUL

## Key Notes
- `@AnchorPoint("ap.3BCYPiR792a2B8I9ZONDwmvN.E")` stays on `TmuxCommunicatorImpl`
- Lambda for `existsChecker` avoids circular dependency
- `sessionExists` becomes private on `TmuxSessionManager`
- Tests: `sessionManager.sessionExists(...)` → `session.exists()`
- Tests: non-existent session test becomes "after kill, exists() returns false"
- `killSession` takes `TmuxSession` not `TmuxSessionName`
- In `TmuxSessionManagerTest`, afterEach cleanup calls `killSession(session)` with `TmuxSession`

## Current State
- COMPLETE: All steps done, BUILD SUCCESSFUL

## Design Decisions
- `TmuxSession` uses a lambda `suspend () -> Boolean` for `existsChecker` to avoid circular dependency between `TmuxSession` and `TmuxSessionManager`
- `TmuxCommunicatorImpl` keeps anchor point
- Interface in same file as impl (per CLAUDE.md standards)
