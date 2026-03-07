# Exploration: Refactor to be more OOP

## Feature Branch
`CC_nid_lfazm1rs99uoj2td5iwljvc7k_E__refactor-to-be-more-oop_sonnet-v4.6`

## Key Files

| File | Role |
|------|------|
| `app/src/main/kotlin/com/glassthought/tmux/TmuxCommunicator.kt` | Sends keystrokes to tmux sessions (`ap.3BCYPiR792a2B8I9ZONDwmvN.E`) |
| `app/src/main/kotlin/com/glassthought/tmux/TmuxSessionManager.kt` | Creates/kills/checks tmux sessions |
| `app/src/main/kotlin/com/glassthought/tmux/data/TmuxSessionName.kt` | Simple data class `TmuxSessionName(sessionName: String)` |
| `app/src/main/kotlin/com/glassthought/tmux/util/TmuxCommandRunner.kt` | Executes tmux CLI commands |
| `app/src/main/kotlin/org/example/App.kt` | Entry point wiring all components |
| `app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt` | Integration tests for TmuxCommunicator |
| `app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt` | Integration tests for TmuxSessionManager |

## Current State

### TmuxCommunicator (class, not interface)
- `sendKeys(session: TmuxSessionName, text: String)` — sends text + Enter
- `sendRawKeys(session: TmuxSessionName, keys: String)` — sends raw keys, no Enter

### TmuxSessionManager
- `createSession(sessionName: String, command: String): TmuxSessionName`
- `killSession(session: TmuxSessionName)`
- `sessionExists(sessionName: String): Boolean` (public)

### App.kt wiring
```kotlin
val commandRunner = TmuxCommandRunner()
val sessionManager = TmuxSessionManager(outFactory, commandRunner)
val communicator = TmuxCommunicator(outFactory, commandRunner)

val session = sessionManager.createSession(sessionName, "claude")  // returns TmuxSessionName
communicator.sendKeys(session, "Write 'hello world from tmux' to /tmp/out")
```

## Required Refactoring

### 1. Extract `TmuxCommunicator` interface
- Create interface `TmuxCommunicator` with `sendKeys(session: TmuxSessionName, text: String)` and `sendRawKeys(session: TmuxSessionName, keys: String)`
- Rename current class to `TmuxCommunicatorImpl` (in the same file is fine)

### 2. Add `TmuxSession` OOP class (new file: `com/glassthought/tmux/TmuxSession.kt`)
```kotlin
class TmuxSession(
    val name: TmuxSessionName,
    private val communicator: TmuxCommunicator,
    private val existsChecker: suspend () -> Boolean,
) {
    suspend fun sendKeys(text: String) = communicator.sendKeys(name, text)
    suspend fun sendRawKeys(keys: String) = communicator.sendRawKeys(name, keys)
    suspend fun exists(): Boolean = existsChecker()
}
```
- Uses lambda `existsChecker` to avoid circular dependency with `TmuxSessionManager`

### 3. Modify `TmuxSessionManager`
- Add `private val communicator: TmuxCommunicator` constructor param
- `createSession()` returns `TmuxSession` instead of `TmuxSessionName`
  - Pass communicator + lambda for `sessionExists` to `TmuxSession`
- `killSession()` takes `TmuxSession` instead of `TmuxSessionName`
- Make `sessionExists()` private (it's internal logic, exposed via `TmuxSession.exists()`)

### 4. Update `App.kt`
- Wire `TmuxCommunicatorImpl` instead of `TmuxCommunicator`
- Pass communicator to `TmuxSessionManager` constructor
- Use `session.sendKeys()` instead of `communicator.sendKeys(session, ...)`

### 5. Update Tests
**TmuxSessionManagerTest:**
- `createdSessions` list type: `TmuxSessionName` → `TmuxSession`
- `sessionManager.sessionExists(sessionName)` → `session.exists()`
- "non-existent session" test: replace with "after kill, exists() returns false" (tests same behavior)

**TmuxCommunicatorTest:**
- `createdSessions` list type: `TmuxSessionName` → `TmuxSession`
- Add `communicator` to `TmuxSessionManager` constructor
- `communicator.sendKeys(session, ...)` → `session.sendKeys(...)`
- Remove standalone `communicator` variable (no longer needed at test level)

## Design Decisions
- **Lambda for existsChecker** in `TmuxSession` avoids circular dependency
- **`TmuxCommunicatorImpl`** keeps the `@AnchorPoint("ap.3BCYPiR792a2B8I9ZONDwmvN.E")` annotation
- **Private `sessionExists`** on `TmuxSessionManager` — clean encapsulation
- **`killSession(session: TmuxSession)`** is more OOP and keeps everything consistent

## Testing Notes
- All tests are integration tests gated by `.config(isIntegTestEnabled())`
- Tests must compile and unit-compile passes (no runIntegTests needed)
- Run: `./gradlew :app:build` to verify compilation
- Run with integ: `./gradlew :app:test -PrunIntegTests=true`
